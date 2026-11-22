package guessmarket.server;

import guessmarket.engine.domain.LmsrXmlData;
import guessmarket.engine.domain.OrderBookXmlData;
import guessmarket.engine.domain.TradingMethodXmlData;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlParser {

    public record ParsedEvent(
            String name,
            String description,
            int commission,
            String commissionType,
            List<String> options,
            TradingMethodXmlData tradingMethod
    ) {}

    public static List<ParsedEvent> parse(InputStream in) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            doc.getDocumentElement().normalize();

            NodeList eventNodes = doc.getElementsByTagName("GM-event");
            if (eventNodes.getLength() == 0) {
                throw new IllegalArgumentException("XML file contains no GM-event elements.");
            }

            List<ParsedEvent> result = new ArrayList<>();
            Set<String> namesInFile = new HashSet<>();

            for (int i = 0; i < eventNodes.getLength(); i++) {
                Element e = (Element) eventNodes.item(i);

                String name = e.getAttribute("name").trim();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Event #" + (i + 1) + " has an empty name attribute.");
                }
                String key = name.toLowerCase();
                if (!namesInFile.add(key)) {
                    throw new IllegalArgumentException("Duplicate event name in uploaded file: '" + name + "'.");
                }

                String description = getSingleText(e, "description");
                if (description == null) {
                    throw new IllegalArgumentException("Event '" + name + "' is missing description.");
                }
                description = description.trim();

                Element commissionEl = getSingleElement(e, "commission");
                if (commissionEl == null) {
                    throw new IllegalArgumentException("Event '" + name + "' is missing commission.");
                }
                String type = commissionEl.getAttribute("type").trim();
                if (!type.equals("on-purchase") && !type.equals("on-close")) {
                    throw new IllegalArgumentException("Event '" + name + "' has invalid commission type: '" + type + "'.");
                }
                int commission;
                try {
                    commission = Integer.parseInt(commissionEl.getTextContent().trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Event '" + name + "' has a non-integer commission.");
                }
                if (commission < 0 || commission > 90) {
                    throw new IllegalArgumentException("Event '" + name + "' must have commission between 0 and 90.");
                }

                List<String> options = new ArrayList<>();
                Element optionsEl = getSingleElement(e, "GM-options");
                if (optionsEl == null) {
                    throw new IllegalArgumentException("Event '" + name + "' is missing GM-options.");
                }
                NodeList optionNodes = optionsEl.getElementsByTagName("GM-option");
                for (int j = 0; j < optionNodes.getLength(); j++) {
                    String opt = optionNodes.item(j).getTextContent().trim();
                    if (!opt.isEmpty()) {
                        options.add(opt);
                    }
                }
                if (options.size() != 2) {
                    throw new IllegalArgumentException("Event '" + name + "' must contain exactly 2 options.");
                }

                Element methodEl = getSingleElement(e, "GM-method");
                if (methodEl == null) {
                    throw new IllegalArgumentException("Event '" + name + "' is missing GM-method.");
                }

                TradingMethodXmlData method;
                Element lmsrEl = getFirstChildElement(methodEl, "GM-LMSR");
                Element obEl = getFirstChildElement(methodEl, "GM-order-book");

                if (lmsrEl != null && obEl != null) {
                    throw new IllegalArgumentException("Event '" + name + "' must contain only one trading method.");
                } else if (lmsrEl != null) {
                    String bText = getSingleText(lmsrEl, "b");
                    if (bText == null) {
                        throw new IllegalArgumentException("Event '" + name + "' LMSR is missing b.");
                    }
                    int b;
                    try {
                        b = Integer.parseInt(bText.trim());
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Event '" + name + "' has a non-integer b.");
                    }
                    if (b <= 0) {
                        throw new IllegalArgumentException("Event '" + name + "' must have b greater than 0.");
                    }
                    method = new LmsrXmlData(b);
                } else if (obEl != null) {
                    boolean allowMint = Boolean.parseBoolean(obEl.getAttribute("allow-mint").trim());
                    int initial;
                    int d;
                    try {
                        initial = Integer.parseInt(obEl.getAttribute("initial").trim());
                        d = Integer.parseInt(obEl.getAttribute("d").trim());
                    } catch (NumberFormatException ex) {
                        throw new IllegalArgumentException("Event '" + name + "' has invalid order-book numbers.");
                    }
                    if (d <= 0) {
                        throw new IllegalArgumentException("Event '" + name + "' must have d greater than 0.");
                    }
                    if (initial < 0) {
                        throw new IllegalArgumentException("Event '" + name + "' must have non-negative initial.");
                    }
                    method = new OrderBookXmlData(allowMint, initial, d);
                } else {
                    throw new IllegalArgumentException("Event '" + name + "' has an unsupported trading method.");
                }

                result.add(new ParsedEvent(name, description, commission, type, options, method));
            }

            return result;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("XML file is malformed or does not match the required format.");
        }
    }

    private static Element getSingleElement(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            if (list.item(i).getParentNode() == parent) {
                return (Element) list.item(i);
            }
        }
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private static Element getFirstChildElement(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getTagName().equals(tag)) {
                return child;
            }
        }
        return null;
    }

    private static String getSingleText(Element parent, String tag) {
        Element el = getSingleElement(parent, tag);
        return el == null ? null : el.getTextContent();
    }
}