// ============================================================================
//
// Copyright (C) 2006-2018 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.components.processing.runtime.fieldselector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.SchemaParseException;
import org.apache.avro.Schema.Field;
import org.apache.avro.generic.IndexedRecord;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.commons.lang3.StringUtils;
import org.talend.components.processing.definition.ProcessingErrorCode;
import org.talend.components.processing.definition.fieldselector.FieldSelectorProperties;
import org.talend.components.processing.definition.fieldselector.SelectorProperties;

import wandou.avpath.Evaluator;
import wandou.avpath.Parser;

public class FieldSelectorDoFn extends DoFn<IndexedRecord, IndexedRecord> {

    private FieldSelectorProperties properties = null;

    private transient Schema outputSchema = null;

    private transient Map<String, Parser.PathSyntax> selectorPath = null;

    @Setup
    public void setup() throws Exception {
    }

    @ProcessElement
    public void selectorPath(ProcessContext context) {
        Map<String, Object> selectedFields = new HashMap<>();
        List<Schema.Field> fieldSchemas = new ArrayList<>();
        if (selectorPath == null) {
            selectorPath = new HashMap();
        }

        for (SelectorProperties selector : properties.selectors.subProperties) {
            String path = selector.path.getValue();
            String field = selector.field.getValue();
            if (StringUtils.isNotEmpty(field) && StringUtils.isNotEmpty(path)) {
                if (!selectorPath.containsKey(path)) {
                    if (path.startsWith(".")) {
                        selectorPath.put(path, new Parser().parse(path));
                    } else {
                        selectorPath.put(path, new Parser().parse("." + path));
                    }
                }
                // Extract field from the input
                List<Evaluator.Ctx> avPathContexts =
                        FieldSelectorUtil.getInputFields(context.element(), selectorPath.get(path));

                if (outputSchema == null) {
                    if (avPathContexts.isEmpty()) {
                        fieldSchemas.add(inferSchema(context.element(), path, field));
                    } else {
                        fieldSchemas.add(retrieveSchema(avPathContexts, path, field));
                    }
                }

                if (!avPathContexts.isEmpty()) {
                    selectedFields.put(field, FieldSelectorUtil.extractValuesFromContext(avPathContexts, path));
                }
            } // else empty selector, we ignoring it.
        }
        if (!selectedFields.isEmpty()) {
            if (outputSchema == null) {
                outputSchema = Schema.createRecord("output_" + context.element().getSchema().getName(), "", "", false,
                        fieldSchemas);
            }
            context.output(FieldSelectorUtil.generateIndexedRecord(selectedFields, outputSchema));
        }
    }

    /**
     * Look are avPathContexts to generate the schema as a {@code Field} for the current element.
     *
     * @param avPathContexts the extracted elements with AVPath
     * @param aVPath the AVPath of the element to generate
     * @param field the name of the output field
     * @return a {@code Field} that describe elements stored in the avPathContexts
     */
    private Field retrieveSchema(List<Evaluator.Ctx> avPathContexts, String aVPath, String field) {
        // If there is at least one field retrieve, it will be placed on a list.
        // We are inferring from the the number of element and the TCOMP path in order to know
        // if we are on a list or not.
        Evaluator.Ctx firstAvPathcontext = avPathContexts.get(0);
        if (avPathContexts.size() > 1 || FieldSelectorUtil.canRetrieveMultipleElements(aVPath)) {
            // create an list for this field
            try {
                return new Field(field, SchemaBuilder.array().items(firstAvPathcontext.schema()), "", "");
            } catch (SchemaParseException e) {
                throw ProcessingErrorCode.createInvalidFieldNameErrorException(e, field);

            }
        } else {
            try {
                return new Field(field, firstAvPathcontext.schema(), "", "");
            } catch (SchemaParseException e) {
                throw ProcessingErrorCode.createInvalidFieldNameErrorException(e, field);

            }

        }
    }

    /**
     * If the AVPath does not point to an element, the avPathContexts will be empty. In this case we try to infer the
     * schema by
     * generating another AVPath without any restriction (ie: predicate or list position)
     *
     * @param input the input record
     * @param aVPath the AVPath of the element to generate
     * @param field the name of the output field
     * @return a {@code Field} that describe elements that can match the aVPath
     */
    private Field inferSchema(IndexedRecord input, String aVPath, String field) {
        String schemaPath = FieldSelectorUtil.changeAVPathToSchemaRetriever(aVPath);
        if (!selectorPath.containsKey(schemaPath)) {
                selectorPath.put(schemaPath, new Parser().parse(schemaPath));
        }
        List<Evaluator.Ctx> avPathContextsForSchema = FieldSelectorUtil.getInputFields(input, selectorPath.get(schemaPath));
        if (!avPathContextsForSchema.isEmpty()) {
            return retrieveSchema(avPathContextsForSchema, aVPath, field);
        } else {
            // Invalid field, this record does not have a schema compatible with the user input.
            // Throw exception
            throw ProcessingErrorCode.createAvpathSyntaxError(aVPath);
        }
    }

    public FieldSelectorDoFn withProperties(FieldSelectorProperties properties) {
        this.properties = properties;
        return this;
    }
}
