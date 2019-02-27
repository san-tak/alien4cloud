package alien4cloud.tosca.parser.impl.advanced;

import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.elasticsearch.common.collect.Maps;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import org.alien4cloud.tosca.model.definitions.Interface;
import alien4cloud.tosca.parser.ParserUtils;
import alien4cloud.tosca.parser.ParsingContextExecution;
import alien4cloud.tosca.parser.impl.base.BaseParserFactory;
import alien4cloud.tosca.parser.impl.base.MapParser;

@Component
public class InterfacesParser extends MapParser<Interface> {
    @Resource
    private BaseParserFactory baseParserFactory;

    public InterfacesParser() {
        super(null, "Interfaces");
    }

    @PostConstruct
    public void init() {
        super.setValueParser(baseParserFactory.getReferencedParser("interface"));
    }

    @Override
    public Map<String, Interface> parse(Node node, ParsingContextExecution context) {
        if (node instanceof MappingNode) {
            Map<String, Interface> interfaces = super.parse(node, context);
            Map<String, Interface> cleanedInterfaces = Maps.newHashMap();
            for (Map.Entry<String, Interface> entry : interfaces.entrySet()) {
                String interfaceType = InterfaceParser.getInterfaceType(entry.getKey());
                if (entry.getValue().getType() == null) {
                    entry.getValue().setType(interfaceType);
                }
                cleanedInterfaces.put(interfaceType, entry.getValue());
            }
            return cleanedInterfaces;
        }
        // In a node type interfaces definition allow to reference an interface type or multiple ones using array, in that case the keyname of the interface is
        // the actual value type.
        Map<String, Interface> interfaces = Maps.newHashMap();
        if (node instanceof SequenceNode) {
            for (Node interfaceTypeNode : ((SequenceNode) node).getValue()) {
                if (interfaceTypeNode instanceof ScalarNode) {
                    addInterfaceFromType((ScalarNode) interfaceTypeNode, interfaces, context);
                } else {
                    ParserUtils.addTypeError(interfaceTypeNode, context.getParsingErrors(), "interface");
                }
            }
        } else if (node instanceof ScalarNode) {
            addInterfaceFromType((ScalarNode) node, interfaces, context);
        } else {
            ParserUtils.addTypeError(node, context.getParsingErrors(), "interfaces");
        }
        return interfaces;
    }

    private void addInterfaceFromType(ScalarNode node, Map<String, Interface> interfaces, ParsingContextExecution context) {
        // FIXME look for interface type in the REPO
        String interfaceType = InterfaceParser.getInterfaceType(node.getValue());
        Interface interfaz = new Interface();
        interfaz.setType(interfaceType);
        interfaces.put(interfaceType, interfaz);
    }
}