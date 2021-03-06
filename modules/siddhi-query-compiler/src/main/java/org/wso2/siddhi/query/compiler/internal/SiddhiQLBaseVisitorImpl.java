/*
 * Copyright (c) 2005 - 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.wso2.siddhi.query.compiler.internal;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.NotNull;
import org.wso2.siddhi.query.api.ExecutionPlan;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.annotation.Element;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.FunctionDefinition;
import org.wso2.siddhi.query.api.definition.StreamDefinition;
import org.wso2.siddhi.query.api.definition.TableDefinition;
import org.wso2.siddhi.query.api.execution.ExecutionElement;
import org.wso2.siddhi.query.api.execution.partition.Partition;
import org.wso2.siddhi.query.api.execution.partition.PartitionType;
import org.wso2.siddhi.query.api.execution.partition.RangePartitionType;
import org.wso2.siddhi.query.api.execution.partition.ValuePartitionType;
import org.wso2.siddhi.query.api.execution.query.Query;
import org.wso2.siddhi.query.api.execution.query.input.handler.*;
import org.wso2.siddhi.query.api.execution.query.input.state.*;
import org.wso2.siddhi.query.api.execution.query.input.stream.*;
import org.wso2.siddhi.query.api.execution.query.output.ratelimit.EventOutputRate;
import org.wso2.siddhi.query.api.execution.query.output.ratelimit.OutputRate;
import org.wso2.siddhi.query.api.execution.query.output.ratelimit.SnapshotOutputRate;
import org.wso2.siddhi.query.api.execution.query.output.ratelimit.TimeOutputRate;
import org.wso2.siddhi.query.api.execution.query.output.stream.*;
import org.wso2.siddhi.query.api.execution.query.selection.OutputAttribute;
import org.wso2.siddhi.query.api.execution.query.selection.Selector;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.expression.Variable;
import org.wso2.siddhi.query.api.expression.condition.Compare;
import org.wso2.siddhi.query.api.expression.constant.*;
import org.wso2.siddhi.query.api.expression.function.AttributeFunction;
import org.wso2.siddhi.query.api.expression.function.AttributeFunctionExtension;
import org.wso2.siddhi.query.compiler.SiddhiQLBaseVisitor;
import org.wso2.siddhi.query.compiler.SiddhiQLParser;
import org.wso2.siddhi.query.compiler.exception.SiddhiParserException;

import java.util.*;

public class SiddhiQLBaseVisitorImpl extends SiddhiQLBaseVisitor {

    private Set<String> activeStreams = new HashSet<String>();

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitParse(@NotNull SiddhiQLParser.ParseContext ctx) {
        return visit(ctx.execution_plan());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public ExecutionPlan visitExecution_plan(@NotNull SiddhiQLParser.Execution_planContext ctx) {
        ExecutionPlan executionPlan = ExecutionPlan.executionPlan();
        for (SiddhiQLParser.Plan_annotationContext annotationContext : ctx.plan_annotation()) {
            executionPlan.annotation((Annotation) visit(annotationContext));
        }
        for (SiddhiQLParser.Definition_streamContext streamContext : ctx.definition_stream()) {
            executionPlan.defineStream((StreamDefinition) visit(streamContext));
        }
        for (SiddhiQLParser.Definition_tableContext tableContext : ctx.definition_table()) {
            executionPlan.defineTable((TableDefinition) visit(tableContext));
        }
        for(SiddhiQLParser.Definition_functionContext functionContext: ctx.definition_function()) {
            executionPlan.defineFunction((FunctionDefinition) visit(functionContext));
        }
        for (SiddhiQLParser.Execution_elementContext executionElementContext : ctx.execution_element()) {
            ExecutionElement executionElement = (ExecutionElement) visit(executionElementContext);
            if (executionElement instanceof Partition) {
                executionPlan.addPartition((Partition) executionElement);

            } else if (executionElement instanceof Query) {
                executionPlan.addQuery((Query) executionElement);

            } else {
                throw newSiddhiParserException(ctx);
            }
        }
        return executionPlan;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitDefinition_stream_final(@NotNull SiddhiQLParser.Definition_stream_finalContext ctx) {
        return visit(ctx.definition_stream());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public StreamDefinition visitDefinition_stream(@NotNull SiddhiQLParser.Definition_streamContext ctx) {
        Source source = (Source) visit(ctx.source());
        if (source.isInnerStream) {
            throw newSiddhiParserException(ctx, " InnerStreams cannot be defined!");
        }
        StreamDefinition streamDefinition = StreamDefinition.id(source.streamId);
        List<SiddhiQLParser.Attribute_nameContext> attribute_names = ctx.attribute_name();
        List<SiddhiQLParser.Attribute_typeContext> attribute_types = ctx.attribute_type();
        for (int i = 0; i < attribute_names.size(); i++) {
            SiddhiQLParser.Attribute_nameContext attributeNameContext = attribute_names.get(i);
            SiddhiQLParser.Attribute_typeContext attributeTypeContext = attribute_types.get(i);
            streamDefinition.attribute((String) visit(attributeNameContext), (Attribute.Type) visit(attributeTypeContext));

        }
        for (SiddhiQLParser.AnnotationContext annotationContext : ctx.annotation()) {
            streamDefinition.annotation((Annotation) visit(annotationContext));
        }
        return streamDefinition;
    }

    @Override
    public Object visitDefinition_function_final(@NotNull SiddhiQLParser.Definition_function_finalContext ctx) {
          return visit(ctx.definition_function());
    }

    @Override
    public FunctionDefinition visitDefinition_function(@NotNull SiddhiQLParser.Definition_functionContext ctx) {
        SiddhiQLParser.Function_nameContext  functionName  = ctx.function_name();
        SiddhiQLParser.Language_nameContext  languageName  = ctx.language_name();
        SiddhiQLParser.Attribute_typeContext attributeType = ctx.attribute_type();
        SiddhiQLParser.Function_bodyContext  functionBody  = ctx.function_body();

        FunctionDefinition functionDefinition = new FunctionDefinition();
        String bodyBlock = functionBody.getText();
        String body = bodyBlock.substring(1,bodyBlock.length()-2);
        functionDefinition.functionID(functionName.getText()).language(languageName.getText()).
                type((Attribute.Type)visit(attributeType)).body(body);
        return functionDefinition;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitDefinition_table_final(@NotNull SiddhiQLParser.Definition_table_finalContext ctx) {
        return visit(ctx.definition_table());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TableDefinition visitDefinition_table(@NotNull SiddhiQLParser.Definition_tableContext ctx) {
        Source source = (Source) visit(ctx.source());
        if (source.isInnerStream) {
            throw newSiddhiParserException(ctx, "'#' cannot be used, because EventTables can't be defined as InnerStream!");
        }
        TableDefinition tableDefinition = TableDefinition.id(source.streamId);
        List<SiddhiQLParser.Attribute_nameContext> attribute_names = ctx.attribute_name();
        List<SiddhiQLParser.Attribute_typeContext> attribute_types = ctx.attribute_type();
        for (int i = 0; i < attribute_names.size(); i++) {
            SiddhiQLParser.Attribute_nameContext attributeNameContext = attribute_names.get(i);
            SiddhiQLParser.Attribute_typeContext attributeTypeContext = attribute_types.get(i);
            tableDefinition.attribute((String) visit(attributeNameContext), (Attribute.Type) visit(attributeTypeContext));

        }
        for (SiddhiQLParser.AnnotationContext annotationContext : ctx.annotation()) {
            tableDefinition.annotation((Annotation) visit(annotationContext));
        }
        return tableDefinition;

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitPartition_final(@NotNull SiddhiQLParser.Partition_finalContext ctx) {
        return visit(ctx.partition());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Partition visitPartition(@NotNull SiddhiQLParser.PartitionContext ctx) {
        Partition partition = Partition.partition();
        for (SiddhiQLParser.Partition_with_streamContext with_streamContext : ctx.partition_with_stream()) {
            partition.with((PartitionType) visit(with_streamContext));
        }
        for (SiddhiQLParser.QueryContext queryContext : ctx.query()) {
            partition.addQuery((Query) visit(queryContext));
        }
        return partition;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public PartitionType visitPartition_with_stream(@NotNull SiddhiQLParser.Partition_with_streamContext ctx) {

        String streamId = (String) visit(ctx.stream_id());

        activeStreams.add(streamId);
        try {
            if (ctx.condition_ranges() != null) {
                return new RangePartitionType(streamId, (RangePartitionType.RangePartitionProperty[]) visit(ctx.condition_ranges()));
            } else if (ctx.attribute() != null) {
                return new ValuePartitionType(streamId, (Expression) visit(ctx.attribute()));
            } else {
                throw newSiddhiParserException(ctx);
            }
        } finally {
            activeStreams.clear();
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public RangePartitionType.RangePartitionProperty[] visitCondition_ranges(@NotNull SiddhiQLParser.Condition_rangesContext ctx) {
        RangePartitionType.RangePartitionProperty[] rangePartitionProperties = new RangePartitionType.RangePartitionProperty[ctx.condition_range().size()];
        List<SiddhiQLParser.Condition_rangeContext> condition_range = ctx.condition_range();
        for (int i = 0; i < condition_range.size(); i++) {
            SiddhiQLParser.Condition_rangeContext rangeContext = condition_range.get(i);
            rangePartitionProperties[i] = (RangePartitionType.RangePartitionProperty) visit(rangeContext);
        }
        return rangePartitionProperties;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitCondition_range(@NotNull SiddhiQLParser.Condition_rangeContext ctx) {
        return new RangePartitionType.RangePartitionProperty((String) ((StringConstant) visit(ctx.string_value())).getValue(), (Expression) visit(ctx.expression()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitQuery_final(@NotNull SiddhiQLParser.Query_finalContext ctx) {
        return visit(ctx.query());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Query visitQuery(@NotNull SiddhiQLParser.QueryContext ctx) {

//        query
//        : annotation* query_input query_section? output_rate? query_output
//        ;

        try {
            Query query = Query.query().from((InputStream) visit(ctx.query_input()));

            if (ctx.query_section() != null) {
                query.select((Selector) visit(ctx.query_section()));
            }
            if (ctx.output_rate() != null) {
                query.output((OutputRate) visit(ctx.output_rate()));
            }
            for (SiddhiQLParser.AnnotationContext annotationContext : ctx.annotation()) {
                query.annotation((Annotation) visit(annotationContext));
            }
            query.outStream((OutputStream) visit(ctx.query_output()));

            return query;

        } finally {
            activeStreams.clear();
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Annotation visitPlan_annotation(@NotNull SiddhiQLParser.Plan_annotationContext ctx) {
        Annotation annotation = new Annotation((String) visit(ctx.name()));

        for (SiddhiQLParser.Annotation_elementContext elementContext : ctx.annotation_element()) {
            annotation.element((Element) visit(elementContext));
        }

        return annotation;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Annotation visitAnnotation(@NotNull SiddhiQLParser.AnnotationContext ctx) {
        Annotation annotation = new Annotation((String) visit(ctx.name()));

        for (SiddhiQLParser.Annotation_elementContext elementContext : ctx.annotation_element()) {
            annotation.element((Element) visit(elementContext));
        }

        return annotation;

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Element visitAnnotation_element(@NotNull SiddhiQLParser.Annotation_elementContext ctx) {
        if (ctx.property_name() != null) {
            return new Element((String) visit(ctx.property_name()), ((StringConstant) visit(ctx.property_value())).getValue());
        } else {
            return new Element(null, ((StringConstant) visit(ctx.property_value())).getValue());
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public SingleInputStream visitStandard_stream(@NotNull SiddhiQLParser.Standard_streamContext ctx) {

//        standard_stream
//        : source (basic_source_stream_handler)* window? (basic_source_stream_handler)*
//        ;

        Source source = (Source) visit(ctx.source());

        BasicSingleInputStream basicSingleInputStream = new BasicSingleInputStream(null, source.streamId,
                source.isInnerStream);

        if (ctx.pre_window_handlers != null) {
            basicSingleInputStream.addStreamHandlers((List<StreamHandler>) visit(ctx.pre_window_handlers));
        }

        if (ctx.window() == null && ctx.post_window_handlers == null) {
            return basicSingleInputStream;
        } else if (ctx.window() != null) {
            SingleInputStream singleInputStream = new SingleInputStream(basicSingleInputStream, (Window) visit(ctx.window()));
            if (ctx.post_window_handlers != null) {
                singleInputStream.addStreamHandlers((List<StreamHandler>) visit(ctx.post_window_handlers));
            }
            return singleInputStream;
        } else {
            throw newSiddhiParserException(ctx);
        }

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitJoin_stream(@NotNull SiddhiQLParser.Join_streamContext ctx) {

//        join_stream
//        :left_source=join_source join right_source=join_source right_unidirectional=UNIDIRECTIONAL (ON expression)? within_time?
//        |left_source=join_source join right_source=join_source (ON expression)? within_time?
//        |left_source=join_source left_unidirectional=UNIDIRECTIONAL join right_source=join_source (ON expression)? within_time?
//        ;

        SingleInputStream leftStream = (SingleInputStream) visit(ctx.left_source);
        SingleInputStream rightStream = (SingleInputStream) visit(ctx.right_source);
        JoinInputStream.Type joinType = (JoinInputStream.Type) visit(ctx.join());
        TimeConstant timeConstant = null;
        Expression onCondition = null;
        JoinInputStream.EventTrigger eventTrigger = null;

        if (ctx.within_time() != null) {
            timeConstant = (TimeConstant) visit(ctx.within_time());
        }

        if (ctx.expression() != null) {
            onCondition = (Expression) visit(ctx.expression());
        }

        if (ctx.right_unidirectional != null) {
            eventTrigger = JoinInputStream.EventTrigger.RIGHT;
        } else if (ctx.left_unidirectional != null) {
            eventTrigger = JoinInputStream.EventTrigger.LEFT;
        } else {
            eventTrigger = JoinInputStream.EventTrigger.ALL;
        }

        return InputStream.joinStream(leftStream, joinType, rightStream, onCondition, timeConstant, eventTrigger);

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitJoin_source(@NotNull SiddhiQLParser.Join_sourceContext ctx) {

//        join_source
//        :source (basic_source_stream_handler)* window? (AS stream_alias)?
//        ;

        Source source = (Source) visit(ctx.source());

        String streamAlias = null;
        if (ctx.stream_alias() != null) {
            streamAlias = (String) visit(ctx.stream_alias());
            activeStreams.remove(ctx.source().getText());
            activeStreams.add(streamAlias);
        }
        BasicSingleInputStream basicSingleInputStream = new BasicSingleInputStream(streamAlias, source.streamId,
                source.isInnerStream);

        if (ctx.basic_source_stream_handlers() != null) {
            basicSingleInputStream.addStreamHandlers((List<StreamHandler>) visit(ctx.basic_source_stream_handlers()));
        }

        if (ctx.window() != null) {
            return new SingleInputStream(basicSingleInputStream, (Window) visit(ctx.window()));
        } else {
            return basicSingleInputStream;
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitPattern_stream(@NotNull SiddhiQLParser.Pattern_streamContext ctx) {
//        pattern_stream
//        :every_pattern_source_chain
//        ;
        StateElement stateElement = ((StateElement) visit(ctx.every_pattern_source_chain()));
        return new StateInputStream(StateInputStream.Type.PATTERN, stateElement);

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitEvery_pattern_source_chain(@NotNull SiddhiQLParser.Every_pattern_source_chainContext ctx) {
//        every_pattern_source_chain
//        : '('every_pattern_source_chain')' within_time?
//        | EVERY '('pattern_source_chain ')' within_time?
//        | every_pattern_source_chain  '->' every_pattern_source_chain
//        | pattern_source_chain
//        | EVERY pattern_source within_time?
//        ;

        if (ctx.every_pattern_source_chain().size() == 1) { // '('every_pattern_source_chain')' within_time?
            StateElement stateElement = ((StateElement) visit(ctx.every_pattern_source_chain(0)));
            if (ctx.within_time() != null) {
                stateElement.setWithin((TimeConstant) visit(ctx.within_time()));
            }
            return stateElement;
        } else if (ctx.every_pattern_source_chain().size() == 2) { // every_pattern_source_chain  '->' every_pattern_source_chain
            return new NextStateElement(((StateElement) visit(ctx.every_pattern_source_chain(0))),
                    ((StateElement) visit(ctx.every_pattern_source_chain(1))));
        } else if (ctx.EVERY() != null) {
            if (ctx.pattern_source_chain() != null) { // EVERY '('pattern_source_chain ')' within_time?
                EveryStateElement everyStateElement = new EveryStateElement((StateElement) visit(ctx.pattern_source_chain()));
                if (ctx.within_time() != null) {
                    everyStateElement.setWithin((TimeConstant) visit(ctx.within_time()));
                }
                return everyStateElement;
            } else if (ctx.pattern_source() != null) { // EVERY pattern_source within_time?
                EveryStateElement everyStateElement = new EveryStateElement((StateElement) visit(ctx.pattern_source()));
                if (ctx.within_time() != null) {
                    everyStateElement.setWithin((TimeConstant) visit(ctx.within_time()));
                }
                return everyStateElement;
            } else {
                throw newSiddhiParserException(ctx);
            }
        } else if (ctx.pattern_source_chain() != null) {  // pattern_source_chain
            StateElement stateElement = ((StateElement) visit(ctx.pattern_source_chain()));
            if (ctx.within_time() != null) {
                stateElement.setWithin((TimeConstant) visit(ctx.within_time()));
            }
            return stateElement;
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitPattern_source_chain(@NotNull SiddhiQLParser.Pattern_source_chainContext ctx) {
//        pattern_source_chain
//        : '('pattern_source_chain')' within_time?
//        | pattern_source_chain  '->' pattern_source_chain
//        | pattern_source within_time?
//        ;

        if (ctx.pattern_source_chain().size() == 1) {
            StateElement stateElement = ((StateElement) visit(ctx.pattern_source_chain(0)));
            if (ctx.within_time() != null) {
                stateElement.setWithin((TimeConstant) visit(ctx.within_time()));
            }
            return stateElement;
        } else if (ctx.pattern_source_chain().size() == 2) {
            return new NextStateElement(((StateElement) visit(ctx.pattern_source_chain(0))),
                    ((StateElement) visit(ctx.pattern_source_chain(1))));
        } else if (ctx.pattern_source() != null) {
            StateElement stateElement = ((StateElement) visit(ctx.pattern_source()));
            if (ctx.within_time() != null) {
                stateElement.setWithin((TimeConstant) visit(ctx.within_time()));
            }
            return stateElement;
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitLogical_stateful_source(@NotNull SiddhiQLParser.Logical_stateful_sourceContext ctx) {

//        logical_stateful_source
//        :NOT standard_stateful_source (AND standard_stateful_source) ?
//        |standard_stateful_source AND standard_stateful_source
//        |standard_stateful_source OR standard_stateful_source
//        ;

        if (ctx.NOT() != null) {
            if (ctx.AND() != null) {
                StreamStateElement streamStateElement1 = (StreamStateElement) visit(ctx.standard_stateful_source(0));
                StreamStateElement streamStateElement2 = (StreamStateElement) visit(ctx.standard_stateful_source(1));
                return State.logicalNotAnd(streamStateElement1, streamStateElement2);
            } else {
                BasicSingleInputStream basicSingleInputStream = (BasicSingleInputStream) visit(ctx.standard_stateful_source(0));
                return State.logicalNot(new StreamStateElement(basicSingleInputStream), null);
            }
        } else if (ctx.AND() != null) {
            StreamStateElement streamStateElement1 = (StreamStateElement) visit(ctx.standard_stateful_source(0));
            StreamStateElement streamStateElement2 = (StreamStateElement) visit(ctx.standard_stateful_source(1));
            return State.logicalAnd(streamStateElement1, streamStateElement2);
        } else if (ctx.OR() != null) {
            StreamStateElement streamStateElement1 = (StreamStateElement) visit(ctx.standard_stateful_source(0));
            StreamStateElement streamStateElement2 = (StreamStateElement) visit(ctx.standard_stateful_source(1));
            return State.logicalOr(streamStateElement1, streamStateElement2);
        } else {
            throw newSiddhiParserException(ctx);
        }

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public CountStateElement visitPattern_collection_stateful_source(@NotNull SiddhiQLParser.Pattern_collection_stateful_sourceContext ctx) {

        StreamStateElement streamStateElement = (StreamStateElement) visit(ctx.standard_stateful_source());

        if (ctx.collect() != null) {
            Object[] minMax = (Object[]) visit(ctx.collect());
            int min = CountStateElement.ANY;
            int max = CountStateElement.ANY;
            if (minMax[0] != null) {
                min = (Integer) minMax[0];
            }
            if (minMax[1] != null) {
                max = (Integer) minMax[1];
            }
            return new CountStateElement(streamStateElement, min, max);
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public StateInputStream visitSequence_stream(@NotNull SiddhiQLParser.Sequence_streamContext ctx) {
//        sequence_stream
//        :EVERY? sequence_source_chain ',' sequence_source_chain
//        ;

        StateElement stateElement1;
        if (ctx.EVERY() != null) {
            stateElement1 = new EveryStateElement((StateElement) visit(ctx.sequence_source()));
        } else {
            stateElement1 = (StateElement) visit(ctx.sequence_source());
        }
        if (ctx.within_time() != null) {
            stateElement1.setWithin((TimeConstant) visit(ctx.within_time()));
        }
        return new StateInputStream(
                StateInputStream.Type.SEQUENCE,
                new NextStateElement(stateElement1, ((StateElement) visit(ctx.sequence_source_chain()))));

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public StateElement visitSequence_source_chain(@NotNull SiddhiQLParser.Sequence_source_chainContext ctx) {
//        sequence_source_chain
//        :'('sequence_source_chain ')' within_time?
//        | sequence_source_chain ',' sequence_source_chain
//        | sequence_source  within_time?
//        ;

        if (ctx.sequence_source_chain().size() == 1) {
            StateElement stateElement = ((StateElement) visit(ctx.sequence_source_chain(0)));
            if (ctx.within_time() != null) {
                stateElement.setWithin((TimeConstant) visit(ctx.within_time()));
            }
            return stateElement;
        } else if (ctx.sequence_source_chain().size() == 2) {
            return new NextStateElement(((StateElement) visit(ctx.sequence_source_chain(0))), ((StateElement) visit(ctx.sequence_source_chain(1))));
        } else if (ctx.sequence_source() != null) {
            StateElement stateElement = ((StateElement) visit(ctx.sequence_source()));
            if (ctx.within_time() != null) {
                stateElement.setWithin((TimeConstant) visit(ctx.within_time()));
            }
            return stateElement;
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public BasicSingleInputStream visitBasic_source(@NotNull SiddhiQLParser.Basic_sourceContext ctx) {

//        basic_source
//        : source (basic_source_stream_handler)*
//        ;

        Source source = (Source) visit(ctx.source());

        BasicSingleInputStream basicSingleInputStream =
                new BasicSingleInputStream(null, source.streamId, source.isInnerStream);

        if (ctx.basic_source_stream_handlers() != null) {
            basicSingleInputStream.addStreamHandlers((List<StreamHandler>) visit(ctx.basic_source_stream_handlers()));
        }

        return basicSingleInputStream;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public List<StreamHandler> visitBasic_source_stream_handlers(@NotNull SiddhiQLParser.Basic_source_stream_handlersContext ctx) {
        List<StreamHandler> streamHandlers = new ArrayList<StreamHandler>();
        for (SiddhiQLParser.Basic_source_stream_handlerContext handlerContext : ctx.basic_source_stream_handler()) {
            streamHandlers.add((StreamHandler) visit(handlerContext));
        }
        return streamHandlers;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public StreamStateElement visitStandard_stateful_source(@NotNull SiddhiQLParser.Standard_stateful_sourceContext ctx) {

//        standard_stateful_source
//        : (event '=')? basic_source
//        ;

        BasicSingleInputStream basicSingleInputStream = (BasicSingleInputStream) visit(ctx.basic_source());
        if (ctx.event() != null) {
            if (basicSingleInputStream.isInnerStream()) {
                activeStreams.remove("#" + basicSingleInputStream.getStreamId());
            } else {
                activeStreams.remove(basicSingleInputStream.getStreamId());
            }
            activeStreams.add(ctx.getText());
            return new StreamStateElement((BasicSingleInputStream) basicSingleInputStream.as((String) visit(ctx.event())));
        } else {
            return new StreamStateElement(basicSingleInputStream);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public CountStateElement visitSequence_collection_stateful_source(@NotNull SiddhiQLParser.Sequence_collection_stateful_sourceContext ctx) {

//        sequence_collection_stateful_source
//        :standard_stateful_source ('<' collect '>'|zero_or_more='*'|zero_or_one='?'|one_or_more='+')
//        ;

        StreamStateElement streamStateElement = (StreamStateElement) visit(ctx.standard_stateful_source());

        if (ctx.one_or_more != null) {
            return new CountStateElement(streamStateElement, 1, CountStateElement.ANY);
        } else if (ctx.zero_or_more != null) {
            return new CountStateElement(streamStateElement, 0, CountStateElement.ANY);
        } else if (ctx.zero_or_one != null) {
            return new CountStateElement(streamStateElement, 0, 1);
        } else if (ctx.collect() != null) {
            Object[] minMax = (Object[]) visit(ctx.collect());
            int min = CountStateElement.ANY;
            int max = CountStateElement.ANY;
            if (minMax[0] != null) {
                min = (Integer) minMax[0];
            }
            if (minMax[1] != null) {
                max = (Integer) minMax[1];
            }
            return new CountStateElement(streamStateElement, min, max);
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public AnonymousInputStream visitAnonymous_stream(@NotNull SiddhiQLParser.Anonymous_streamContext ctx) {

        if (ctx.anonymous_stream() != null) {
            return (AnonymousInputStream) visit(ctx.anonymous_stream());
        }

        Set<String> activeStreamsBackup = activeStreams;

        try {
            activeStreams = new HashSet<String>();

            Query query = Query.query().from((InputStream) visit(ctx.query_input()));

            if (ctx.query_section() != null) {
                query.select((Selector) visit(ctx.query_section()));
            }
            if (ctx.output_rate() != null) {
                query.output((OutputRate) visit(ctx.output_rate()));
            }

            if (ctx.output_event_type() != null) {
                query.outStream(new ReturnStream((OutputStream.OutputEventType) visit(ctx.output_event_type())));
            } else {
                query.outStream(new ReturnStream());
            }

            return new AnonymousInputStream(query);

        } finally {
            activeStreams.clear();
            activeStreams = activeStreamsBackup;
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Filter visitFilter(@NotNull SiddhiQLParser.FilterContext ctx) {
        return new Filter((Expression) visit(ctx.expression()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public StreamFunction visitStream_function(@NotNull SiddhiQLParser.Stream_functionContext ctx) {
        AttributeFunction attributeFunction = (AttributeFunction) visit(ctx.function_operation());
        if (attributeFunction instanceof AttributeFunctionExtension) {
            return new StreamFunctionExtension(((AttributeFunctionExtension) attributeFunction).getNamespace(), attributeFunction.getFunction(), attributeFunction.getParameters());
        } else {
            return new StreamFunction(attributeFunction.getFunction(), attributeFunction.getParameters());
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Window visitWindow(@NotNull SiddhiQLParser.WindowContext ctx) {
        AttributeFunction attributeFunction = (AttributeFunction) visit(ctx.function_operation());
        if (attributeFunction instanceof AttributeFunctionExtension) {
            return new WindowExtension(((AttributeFunctionExtension) attributeFunction).getNamespace(), attributeFunction.getFunction(), attributeFunction.getParameters());
        } else {
            return new Window(attributeFunction.getFunction(), attributeFunction.getParameters());
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Selector visitQuery_section(@NotNull SiddhiQLParser.Query_sectionContext ctx) {

//        query_section
//        :(SELECT ('*'| (output_attribute (',' output_attribute)* ))) group_by? having?
//        ;

        Selector selector = new Selector();

        List<OutputAttribute> attributeList = new ArrayList<OutputAttribute>(ctx.output_attribute().size());
        for (SiddhiQLParser.Output_attributeContext output_attributeContext : ctx.output_attribute()) {
            attributeList.add((OutputAttribute) visit(output_attributeContext));
        }
        selector.addSelectionList(attributeList);

        if (ctx.group_by() != null) {
            selector.addGroupByList((List<Variable>) visit(ctx.group_by()));
        }
        if (ctx.having() != null) {
            selector.having((Expression) visit(ctx.having()));
        }

        return selector;
    }


    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public List<Variable> visitGroup_by(@NotNull SiddhiQLParser.Group_byContext ctx) {
        List<Variable> variableList = new ArrayList<Variable>(ctx.attribute_reference().size());
        for (SiddhiQLParser.Attribute_referenceContext attributeReferenceContext : ctx.attribute_reference()) {
            variableList.add((Variable) visit(attributeReferenceContext));
        }
        return variableList;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitHaving(@NotNull SiddhiQLParser.HavingContext ctx) {
        return (Expression) visit(ctx.expression());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public OutputStream visitQuery_output(@NotNull SiddhiQLParser.Query_outputContext ctx) {
//        query_output
//        :INSERT output_event_type? INTO target
//        |DELETE target (FOR output_event_type)? (ON expression)?
//        |UPDATE target (FOR output_event_type)? (ON expression)?
//        |RETURN output_event_type?
//        ;

        if (ctx.INSERT() != null) {
            Source source = (Source) visit(ctx.target());
            if (ctx.output_event_type() != null) {
                return new InsertIntoStream(source.streamId, source.isInnerStream,
                        (OutputStream.OutputEventType) visit(ctx.output_event_type()));
            } else {
                return new InsertIntoStream(source.streamId, source.isInnerStream);
            }
        } else if (ctx.DELETE() != null) {
            Source source = (Source) visit(ctx.target());
            if (source.isInnerStream) {
                throw newSiddhiParserException(ctx, "DELETE can be only used with EventTables!");
            }
            if (ctx.output_event_type() != null) {
                return new DeleteStream(source.streamId,
                        (OutputStream.OutputEventType) visit(ctx.output_event_type()),
                        (Expression) visit(ctx.expression()));
            } else {
                return new DeleteStream(source.streamId, (Expression) visit(ctx.expression()));
            }
        } else if (ctx.UPDATE() != null) {
            Source source = (Source) visit(ctx.target());
            if (source.isInnerStream) {
                throw newSiddhiParserException(ctx, "DELETE can be only used with EventTables!");
            }
            if (ctx.output_event_type() != null) {
                return new UpdateStream(source.streamId,
                        (OutputStream.OutputEventType) visit(ctx.output_event_type()),
                        (Expression) visit(ctx.expression()));
            } else {
                return new UpdateStream(source.streamId, (Expression) visit(ctx.expression()));
            }
        } else if (ctx.RETURN() != null) {
            if (ctx.output_event_type() != null) {
                return new ReturnStream((OutputStream.OutputEventType) visit(ctx.output_event_type()));
            } else {
                return new ReturnStream();
            }
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public OutputStream.OutputEventType visitOutput_event_type(@NotNull SiddhiQLParser.Output_event_typeContext ctx) {
//        output_event_type
//        : ALL EVENTS | ALL RAW EVENTS | EXPIRED EVENTS | EXPIRED RAW EVENTS | CURRENT? EVENTS
//        ;

        if (ctx.ALL() != null) {
            if (ctx.RAW() != null) {
                return OutputStream.OutputEventType.ALL_RAW_EVENTS;
            } else {
                return OutputStream.OutputEventType.ALL_EVENTS;
            }
        } else if (ctx.EXPIRED() != null) {
            if (ctx.RAW() != null) {
                return OutputStream.OutputEventType.EXPIRED_EVENTS;
            } else {
                return OutputStream.OutputEventType.EXPIRED_RAW_EVENTS;
            }
        } else {
            return OutputStream.OutputEventType.CURRENT_EVENTS;
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public OutputRate visitOutput_rate(@NotNull SiddhiQLParser.Output_rateContext ctx) {
//        output_rate
//                : OUTPUT output_rate_type? EVERY ( time_value | INT_LITERAL EVENTS )
//                | OUTPUT SNAPSHOT EVERY time_value
//        ;

        if (ctx.SNAPSHOT() != null) {
            return new SnapshotOutputRate(((TimeConstant) visit(ctx.time_value())).value());
        } else if (ctx.time_value() != null) {
            TimeOutputRate timeOutputRate = new TimeOutputRate(((TimeConstant) visit(ctx.time_value())).value());
            if (ctx.output_rate_type() != null) {
                timeOutputRate.output((OutputRate.Type) visit(ctx.output_rate_type()));
            }
            return timeOutputRate;
        } else if (ctx.EVENTS() != null) {
            EventOutputRate eventOutputRate = new EventOutputRate(Integer.parseInt(ctx.INT_LITERAL().getText()));
            if (ctx.output_rate_type() != null) {
                eventOutputRate.output((OutputRate.Type) visit(ctx.output_rate_type()));
            }
            return eventOutputRate;
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitOutput_rate_type(@NotNull SiddhiQLParser.Output_rate_typeContext ctx) {

//        output_rate_type
//                : ALL
//                | LAST
//                | FIRST
//        ;
        if (ctx.ALL() != null) {
            return OutputRate.Type.ALL;
        } else if (ctx.LAST() != null) {
            return OutputRate.Type.LAST;
        } else if (ctx.FIRST() != null) {
            return OutputRate.Type.FIRST;
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitOutput_attribute(@NotNull SiddhiQLParser.Output_attributeContext ctx) {

//        output_attribute
//                :attribute AS attribute_name
//                |attribute_reference
//        ;
        if (ctx.AS() != null) {
            return new OutputAttribute((String) visit(ctx.attribute_name()), (Expression) visit(ctx.attribute()));
        } else {
            return new OutputAttribute((Variable) visit(ctx.attribute_reference()));
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitOr_math_operation(@NotNull SiddhiQLParser.Or_math_operationContext ctx) {
        if (ctx.OR() != null) {
            return Expression.or((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitAnd_math_operation(@NotNull SiddhiQLParser.And_math_operationContext ctx) {
        if (ctx.AND() != null) {
            return Expression.and((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitEquality_math_operation(@NotNull SiddhiQLParser.Equality_math_operationContext ctx) {
        if (ctx.eq != null) {
            return Expression.compare((Expression) visit(ctx.math_operation(0)), Compare.Operator.EQUAL, (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.not_eq != null) {
            return Expression.compare((Expression) visit(ctx.math_operation(0)), Compare.Operator.NOT_EQUAL, (Expression) visit(ctx.math_operation(1)));
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitGreaterthan_lessthan_math_operation(@NotNull SiddhiQLParser.Greaterthan_lessthan_math_operationContext ctx) {
        if (ctx.gt != null) {
            return Expression.compare((Expression) visit(ctx.math_operation(0)), Compare.Operator.GREATER_THAN, (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.lt != null) {
            return Expression.compare((Expression) visit(ctx.math_operation(0)), Compare.Operator.LESS_THAN, (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.gt_eq != null) {
            return Expression.compare((Expression) visit(ctx.math_operation(0)), Compare.Operator.GREATER_THAN_EQUAL, (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.lt_eq != null) {
            return Expression.compare((Expression) visit(ctx.math_operation(0)), Compare.Operator.LESS_THAN_EQUAL, (Expression) visit(ctx.math_operation(1)));
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitAddition_math_operation(@NotNull SiddhiQLParser.Addition_math_operationContext ctx) {
        if (ctx.add != null) {
            return Expression.add((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.substract != null) {
            return Expression.subtract((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitMultiplication_math_operation(@NotNull SiddhiQLParser.Multiplication_math_operationContext ctx) {
        if (ctx.multiply != null) {
            return Expression.multiply((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.devide != null) {
            return Expression.divide((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else if (ctx.mod != null) {
            return Expression.mod((Expression) visit(ctx.math_operation(0)), (Expression) visit(ctx.math_operation(1)));
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Expression visitNot_math_operation(@NotNull SiddhiQLParser.Not_math_operationContext ctx) {
        return Expression.not((Expression) visit(ctx.math_operation()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitIn_math_operation(@NotNull SiddhiQLParser.In_math_operationContext ctx) {
        return Expression.in((Expression) visit(ctx.math_operation()), (String) visit(ctx.name()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitBasic_math_operation(@NotNull SiddhiQLParser.Basic_math_operationContext ctx) {
        if (ctx.math_operation() != null) {
            return visit(ctx.math_operation());
        } else if (ctx.attribute_reference() != null) {
            return visit(ctx.attribute_reference());
        } else if (ctx.constant_value() != null) {
            return visit(ctx.constant_value());
        } else if (ctx.null_check() != null) {
            return visit(ctx.null_check());
        } else if (ctx.function_operation() != null) {
            return visit(ctx.function_operation());
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitFunction_operation(@NotNull SiddhiQLParser.Function_operationContext ctx) {
        if (ctx.function_namespace() != null) {
            if (ctx.attribute_list() != null) {
                return Expression.function((String) visit(ctx.function_namespace()), (String) visit(ctx.function_id()), (Expression[]) visit(ctx.attribute_list()));
            } else {
                return Expression.function((String) visit(ctx.function_namespace()), (String) visit(ctx.function_id()));
            }

        } else {
            if (ctx.attribute_list() != null) {
                return Expression.function((String) visit(ctx.function_id()), (Expression[]) visit(ctx.attribute_list()));
            } else {
                return Expression.function((String) visit(ctx.function_id()));
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitAttribute_list(@NotNull SiddhiQLParser.Attribute_listContext ctx) {
        Expression[] expressionArray = new Expression[ctx.attribute().size()];
        List<SiddhiQLParser.AttributeContext> attribute = ctx.attribute();
        for (int i = 0; i < attribute.size(); i++) {
            SiddhiQLParser.AttributeContext attributeContext = attribute.get(i);
            expressionArray[i] = (Expression) visit(attributeContext);
        }
        return expressionArray;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitNull_check(@NotNull SiddhiQLParser.Null_checkContext ctx) {
        if (ctx.stream_reference() != null) {
            StreamReference streamReference = (StreamReference) visit(ctx.stream_reference());
            if (streamReference.isInnerStream) {   //InnerStream
                if (streamReference.streamIndex != null) {
                    return Expression.isNullInnerStream(streamReference.streamId, streamReference.streamIndex);
                } else {
                    return Expression.isNullInnerStream(streamReference.streamId);
                }
            } else {
                if (activeStreams.contains(streamReference.streamId)) { //Stream
                    if (streamReference.streamIndex != null) {
                        return Expression.isNullStream(streamReference.streamId, streamReference.streamIndex);
                    } else {
                        return Expression.isNullStream(streamReference.streamId);
                    }
                } else { //Attribute
                    return Expression.isNull(Expression.variable(streamReference.streamId));
                }
            }
        } else { //attribute_reference
            return Expression.isNull((Variable) visit(ctx.attribute_reference()));
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitStream_reference(@NotNull SiddhiQLParser.Stream_referenceContext ctx) {
        StreamReference streamReference = new StreamReference();
        if (ctx.hash != null) {
            streamReference.isInnerStream = true;
        }
        streamReference.streamId = (String) visit(ctx.name());
        if (ctx.attribute_index() != null) {
            streamReference.streamIndex = (Integer) visit(ctx.attribute_index());
        }
        return streamReference;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Variable visitAttribute_reference(@NotNull SiddhiQLParser.Attribute_referenceContext ctx) {

//        attribute_reference
//        : hash1='#'? name1=name ('['attribute_index1=attribute_index']')? (hash2='#' name2=name ('['attribute_index2=attribute_index']')?)? '.'  attribute_name
//        | attribute_name
//        ;

        Variable variable = Expression.variable((String) visit(ctx.attribute_name()));

        if (ctx.name1 != null && ctx.name2 != null) { //Stream and Function
            variable.setStreamId(ctx.hash1 != null, (String) visit(ctx.name1));
            if (ctx.attribute_index1 != null) {
                variable.setStreamIndex((Integer) visit(ctx.attribute_index1));
            }

            variable.setFunctionId((String) visit(ctx.name2));
            if (ctx.attribute_index2 != null) {
                variable.setFunctionIndex((Integer) visit(ctx.attribute_index2));
            }
        } else if (ctx.name1 != null) {   //name2 == null
            if (ctx.hash1 == null) {   //Stream
                variable.setStreamId((String) visit(ctx.name1));
                if (ctx.attribute_index1 != null) {
                    variable.setStreamIndex((Integer) visit(ctx.attribute_index1));
                }
            } else {  //InnerStream or Function
                String name = (String) visit(ctx.name1);

                if (activeStreams.contains("#" + name)) { //InnerStream
                    variable.setStreamId(true, name);
                    if (ctx.attribute_index1 != null) {
                        variable.setStreamIndex((Integer) visit(ctx.attribute_index1));
                    }

                } else {//Function
                    variable.setFunctionId(name);
                    if (ctx.attribute_index1 != null) {
                        variable.setFunctionIndex((Integer) visit(ctx.attribute_index1));
                    }
                }
            }
        }
        return variable;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Integer visitAttribute_index(@NotNull SiddhiQLParser.Attribute_indexContext ctx) {
        int index;
        if (ctx.LAST() != null) {
            index = -1;
            if (ctx.INT_LITERAL() != null) {
                index -= Integer.parseInt(ctx.INT_LITERAL().getText());
            }
        } else {
            index = Integer.parseInt(ctx.INT_LITERAL().getText());
        }
        return index;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitProperty_name(@NotNull SiddhiQLParser.Property_nameContext ctx) {

        StringBuilder stringBuilder = new StringBuilder();
        for (SiddhiQLParser.NameContext nameContext : ctx.name()) {
            stringBuilder.append(".").append(visit(nameContext));
        }
        return stringBuilder.substring(1);
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Source visitSource(@NotNull SiddhiQLParser.SourceContext ctx) {
        Source source = new Source();
        source.streamId = (String) visit(ctx.stream_id());
        source.isInnerStream = ctx.inner != null;

        activeStreams.add(ctx.getText());

        return source;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitName(@NotNull SiddhiQLParser.NameContext ctx) {
        if (ctx.id() != null) {
            return visit(ctx.id());
        } else if (ctx.keyword() != null) {
            return visit(ctx.keyword());
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object[] visitCollect(@NotNull SiddhiQLParser.CollectContext ctx) {
        Object[] minMax = new Object[2];

        if (ctx.start == null && ctx.end == null) {
            Integer value = Integer.parseInt(ctx.INT_LITERAL(0).getText());
            minMax[0] = value;
            minMax[1] = value;
        } else {
            if (ctx.start != null) {
                minMax[0] = Integer.parseInt(ctx.start.getText());
            }
            if (ctx.end != null) {
                minMax[1] = Integer.parseInt(ctx.end.getText());
            }
        }
        return minMax;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitAttribute_type(@NotNull SiddhiQLParser.Attribute_typeContext ctx) {

//        attribute_type
//                :STRING
//                |INT
//                |LONG
//                |FLOAT
//                |DOUBLE
//                |BOOL
//                |OBJECT
//        ;

        if (ctx.STRING() != null) {
            return Attribute.Type.STRING;
        } else if (ctx.INT() != null) {
            return Attribute.Type.INT;
        } else if (ctx.LONG() != null) {
            return Attribute.Type.LONG;
        } else if (ctx.FLOAT() != null) {
            return Attribute.Type.FLOAT;
        } else if (ctx.DOUBLE() != null) {
            return Attribute.Type.DOUBLE;
        } else if (ctx.BOOL() != null) {
            return Attribute.Type.BOOL;
        } else if (ctx.OBJECT() != null) {
            return Attribute.Type.OBJECT;
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public JoinInputStream.Type visitJoin(@NotNull SiddhiQLParser.JoinContext ctx) {
        if (ctx.OUTER() != null) {
            throw newSiddhiParserException(ctx, "OUTER JOIN not yet supported!");
        }
        return JoinInputStream.Type.JOIN;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Constant visitConstant_value(@NotNull SiddhiQLParser.Constant_valueContext ctx) {

//        constant_value
//                :bool_value
//                |signed_double_value
//                |signed_float_value
//                |signed_long_value
//                |signed_int_value
//                |time_value
//                |string_value
//        ;


        if (ctx.bool_value() != null) {
            return Expression.value(((BoolConstant) visit(ctx.bool_value())).getValue());
        } else if (ctx.signed_double_value() != null) {
            return Expression.value(((DoubleConstant) visit(ctx.signed_double_value())).getValue());
        } else if (ctx.signed_float_value() != null) {
            return Expression.value(((FloatConstant) visit(ctx.signed_float_value())).getValue());
        } else if (ctx.signed_long_value() != null) {
            return Expression.value(((LongConstant) visit(ctx.signed_long_value())).getValue());
        } else if (ctx.signed_int_value() != null) {
            return Expression.value(((IntConstant) visit(ctx.signed_int_value())).getValue());
        } else if (ctx.time_value() != null) {
            return (TimeConstant) visit(ctx.time_value());
        } else if (ctx.string_value() != null) {
            return Expression.value(((StringConstant) visit(ctx.string_value())).getValue());
        } else {
            throw newSiddhiParserException(ctx);
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public String visitId(@NotNull SiddhiQLParser.IdContext ctx) {
        return ctx.getText();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public Object visitKeyword(@NotNull SiddhiQLParser.KeywordContext ctx) {
        return ctx.getText();
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitTime_value(@NotNull SiddhiQLParser.Time_valueContext ctx) {
        TimeConstant timeValueInMillis = Expression.Time.milliSec(0);
        if (ctx.millisecond_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.millisecond_value())).value());
        }
        if (ctx.second_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.second_value())).value());
        }
        if (ctx.minute_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.minute_value())).value());
        }
        if (ctx.hour_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.hour_value())).value());
        }
        if (ctx.day_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.day_value())).value());
        }
        if (ctx.week_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.week_value())).value());
        }
        if (ctx.month_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.month_value())).value());
        }
        if (ctx.year_value() != null) {
            timeValueInMillis.milliSec(((TimeConstant) visit(ctx.year_value())).value());
        }
        return timeValueInMillis;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitYear_value(@NotNull SiddhiQLParser.Year_valueContext ctx) {
        return Expression.Time.year(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitMonth_value(@NotNull SiddhiQLParser.Month_valueContext ctx) {
        return Expression.Time.month(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitWeek_value(@NotNull SiddhiQLParser.Week_valueContext ctx) {
        return Expression.Time.week(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitDay_value(@NotNull SiddhiQLParser.Day_valueContext ctx) {
        return Expression.Time.day(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitHour_value(@NotNull SiddhiQLParser.Hour_valueContext ctx) {
        return Expression.Time.hour(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitMinute_value(@NotNull SiddhiQLParser.Minute_valueContext ctx) {
        return Expression.Time.minute(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitSecond_value(@NotNull SiddhiQLParser.Second_valueContext ctx) {
        return Expression.Time.sec(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public TimeConstant visitMillisecond_value(@NotNull SiddhiQLParser.Millisecond_valueContext ctx) {
        return Expression.Time.milliSec(Long.parseLong(ctx.INT_LITERAL().getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public DoubleConstant visitSigned_double_value(@NotNull SiddhiQLParser.Signed_double_valueContext ctx) {
        return Expression.value(Double.parseDouble(ctx.getText()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public LongConstant visitSigned_long_value(@NotNull SiddhiQLParser.Signed_long_valueContext ctx) {
        return Expression.value(Long.parseLong(ctx.getText().replaceFirst("[lL]", "")));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public FloatConstant visitSigned_float_value(@NotNull SiddhiQLParser.Signed_float_valueContext ctx) {
        return Expression.value(Float.parseFloat(ctx.getText()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public IntConstant visitSigned_int_value(@NotNull SiddhiQLParser.Signed_int_valueContext ctx) {
        return Expression.value(Integer.parseInt(ctx.getText()));

    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public BoolConstant visitBool_value(@NotNull SiddhiQLParser.Bool_valueContext ctx) {
        return Expression.value("true".equalsIgnoreCase(ctx.getText()));
    }

    /**
     * {@inheritDoc}
     * <p/>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public StringConstant visitString_value(@NotNull SiddhiQLParser.String_valueContext ctx) {
        return Expression.value(ctx.STRING_LITERAL().getText());
    }

    public SiddhiParserException newSiddhiParserException(ParserRuleContext context) {
        return new SiddhiParserException("You have an error in your SiddhiQL near '" + context.getText() + "'  line " + context.start.getLine() + ":" + context.start.getCharPositionInLine());
    }

    public SiddhiParserException newSiddhiParserException(ParserRuleContext context, String message) {
        return new SiddhiParserException("You have an error in your SiddhiQL near '" + context.getText() + "'  line " + context.start.getLine() + ":" + context.start.getCharPositionInLine() + ", " + message);
    }

    private class Source {

        private String streamId;
        private boolean isInnerStream;
    }

    private class StreamReference {

        private String streamId;
        private boolean isInnerStream;
        private Integer streamIndex;
    }
}
