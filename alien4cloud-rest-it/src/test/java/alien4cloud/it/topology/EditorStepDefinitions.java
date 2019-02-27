package alien4cloud.it.topology;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.alien4cloud.tosca.editor.operations.AbstractEditorOperation;
import org.apache.commons.lang.StringUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import alien4cloud.it.Context;
import alien4cloud.it.common.CommonStepDefinitions;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.topology.TopologyDTO;
import cucumber.api.DataTable;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.When;
import gherkin.formatter.model.DataTableRow;

/**
 * Steps to manage the editor.
 */
public class EditorStepDefinitions {
    // Keep a local context for topology dto
    public static TopologyDTO TOPOLOGY_DTO = null;

    @Given("^I get the current topology$")
    public void i_get_the_current_topology() throws Throwable {
        // Call the rest controller to get the topology DTO and register it
        do_i_get_the_current_topology();
    }

    public static void do_i_get_the_current_topology() throws Throwable {
        // Call the rest controller to get the topology DTO and register it
        String topologyId = Context.getInstance().getTopologyId();

        Context.getInstance().registerRestResponse(Context.getRestClientInstance().get("/rest/v1/topologies/" + topologyId));
        trySetTopologyDto();
    }

    @Given("^I execute the operation$")
    public void i_execute_the_operation(DataTable operationDT) throws Throwable {
        Map<String, String> operationMap = Maps.newLinkedHashMap();
        for (DataTableRow row : operationDT.getGherkinRows()) {
            operationMap.put(row.getCells().get(0), row.getCells().get(1));
        }

        do_i_execute_the_operation(operationMap);
    }

    public static void do_i_execute_the_operation(Map<String, String> operationMap) throws Throwable {
        Class operationClass = Class.forName(operationMap.get("type"));
        AbstractEditorOperation operation = (AbstractEditorOperation) operationClass.newInstance();
        EvaluationContext operationContext = new StandardEvaluationContext(operation);
        SpelParserConfiguration config = new SpelParserConfiguration(true, true);
        SpelExpressionParser parser = new SpelExpressionParser(config);
        for (Map.Entry<String, String> operationEntry : operationMap.entrySet()) {
            if (!"type".equals(operationEntry.getKey())) {
                parser.parseRaw(operationEntry.getKey()).setValue(operationContext, operationEntry.getValue());
            }
        }
        do_i_execute_the_operation(operation);
    }

    public static void do_i_execute_the_operation(AbstractEditorOperation operation) throws Throwable {
        String topologyId = Context.getInstance().getTopologyId();
        operation.setPreviousOperationId(getLastOperationId());
        // Call execute rest service and set the topology DTO to the context
        Context.getInstance()
                .registerRestResponse(Context.getRestClientInstance().postJSon("/rest/v2/editor/" + topologyId + "/execute", JsonUtil.toString(operation)));
        trySetTopologyDto();
    }

    public static String getLastOperationId() {

        if (TOPOLOGY_DTO == null || TOPOLOGY_DTO.getLastOperationIndex() == -1) {
            // no previous operations
            return null;
        } else {
            return TOPOLOGY_DTO.getOperations().get(TOPOLOGY_DTO.getLastOperationIndex()).getId();
        }
    }

    @Given("^I (successfully\\s)?save the topology$")
    public void i_save_the_topology(String successfully) throws Throwable {
        do_i_save_the_topology();
        CommonStepDefinitions.validateIfNeeded(StringUtils.isNotBlank(successfully));
    }

    public static void do_i_save_the_topology() throws Throwable {
        String topologyId = Context.getInstance().getTopologyId();
        String lastOperationId = TOPOLOGY_DTO.getOperations().get(TOPOLOGY_DTO.getLastOperationIndex()).getId();

        Context.getInstance()
                .registerRestResponse(Context.getRestClientInstance().postJSon("/rest/v2/editor/" + topologyId + "?lastOperationId=" + lastOperationId, ""));
        trySetTopologyDto();
    }

    public static void trySetTopologyDto() {
        try {
            TOPOLOGY_DTO = JsonUtil.read(Context.getInstance().getRestResponse(), TopologyDTO.class, Context.getJsonMapper()).getData();
        } catch (Exception e) {
            // This may fail as the latest rest call may fail based on test scenario but this is a shortcut for all successfull scenario.
        }
    }

    @When("^I upload a file located at \"([^\"]*)\" to the archive path \"([^\"]*)\"$")
    public void iUploadAFileLocatedAtToTheArchivePath(String localFile, String archivePath) throws Throwable {
        String topologyId = Context.getInstance().getTopologyId();
        String url = "/rest/v2/editor/" + topologyId + "/upload";
        InputStream artifactStream = Files.newInputStream(Paths.get(localFile));
        Context.getInstance()
                .registerRestResponse(Context.getRestClientInstance().postMultipart(url, Paths.get(localFile).getFileName().toString(), artifactStream,
                        ImmutableMap.<String, String> builder().put("path", archivePath).put("lastOperationId", String.valueOf(getLastOperationId())).build()));
        trySetTopologyDto();
    }

}
