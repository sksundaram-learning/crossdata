package com.stratio.meta.metadata;


import com.datastax.driver.core.ColumnMetadata;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * Class that processes the options found in a Lucene index and
 * builds a map with the set of columns mapped by the specified
 * Lucene index. Notice that a Lucene index may cover several
 * columns.
 */
public class LuceneIndexHelper {

    /**
     * Class logger.
     */
     private static final Logger _logger = Logger.getLogger(LuceneIndexHelper.class.getName());


    public Map<String, List<CustomIndexMetadata>> getIndexedColumns(ColumnMetadata column){
        Map<String, List<CustomIndexMetadata>> result = null;
        return result;
    }

    /**
     * Process the list of options found in a Lucene index in the form of a JSON string.
     * @param options The options.
     * @return The map with the columns and associated indexes or empty if the JSON cannot
     * be processed.
     */
    protected Map<String, List<CustomIndexMetadata>> processLuceneOptions(ColumnMetadata metadata, String options){
        Map<String, List<CustomIndexMetadata>> result = null;
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        JsonFactory factory = mapper.getJsonFactory();
        JsonParser jp = null;
        try {
            jp = factory.createJsonParser(options);
            JsonNode root = mapper.readTree(jp);
            JsonNode schema = root.get("schema");
            if(schema != null){
                JsonNode node = null;
                String schemaString = schema.toString()
                        .substring(1, schema.toString().length() - 1)
                        .replace("\\", "");

                JsonNode schemaRoot = mapper.readTree(factory.createJsonParser(schemaString));
                if(schemaRoot != null && schemaRoot.get("fields") != null){
                    JsonNode fields = schemaRoot.get("fields");
                    result = processLuceneFields(metadata, fields);
                }else{
                    _logger.error("Fields not found in Lucene index with JSON: " + root.toString());
                }
            }else{
                _logger.error("Schema not found in Lucene index with JSON: " + root.toString());
            }
        } catch (IOException e) {
            _logger.error("Cannot process Lucene index options", e);
        } finally {
            try {
                if (jp != null) { jp.close(); }
            } catch (IOException e) { _logger.error("Cannot close JSON parser"); }
        }

        return result;
    }

    /**
     * Process a JSON list of fields mapped by a Lucene index.
     * @param metadata Column metadata.
     * @param fields The JSON representation of the fields.
     * @return The map of columns indexed by the current index.
     */
    public Map<String, List<CustomIndexMetadata>> processLuceneFields(ColumnMetadata metadata, JsonNode fields){
        Map<String, List<CustomIndexMetadata>> result = new HashMap<String, List<CustomIndexMetadata>>();
        Iterator<String> fieldIt = fields.getFieldNames();
        while(fieldIt.hasNext()){
            String fieldName = fieldIt.next();
            //System.out.println("field: " + fieldName + " -> " + fields.get(fieldName).toString());
            CustomIndexMetadata cim = new CustomIndexMetadata(metadata, IndexType.CUSTOM);
            cim.setIndexOptions(fields.get(fieldName).toString());
            List<CustomIndexMetadata> indexes = result.get(fieldName);
            if(indexes == null){
                indexes = new ArrayList<>();
                result.put(fieldName, indexes);
            }
            indexes.add(cim);
        }
        return result;
    }

}