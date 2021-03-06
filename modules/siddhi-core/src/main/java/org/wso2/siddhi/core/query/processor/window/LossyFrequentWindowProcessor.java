/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org)
 * All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.siddhi.core.query.processor.window;

import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.ComplexEvent;
import org.wso2.siddhi.core.event.ComplexEventChunk;
import org.wso2.siddhi.core.event.MetaComplexEvent;
import org.wso2.siddhi.core.event.stream.StreamEvent;
import org.wso2.siddhi.core.event.stream.StreamEventCloner;
import org.wso2.siddhi.core.executor.ConstantExpressionExecutor;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.processor.Processor;
import org.wso2.siddhi.core.table.EventTable;
import org.wso2.siddhi.core.util.finder.Finder;
import org.wso2.siddhi.core.util.parser.SimpleFinderParser;
import org.wso2.siddhi.query.api.expression.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LossyFrequentWindowProcessor extends WindowProcessor implements FindableProcessor{

    private ConcurrentHashMap<String, LossyCount> countMap = new ConcurrentHashMap<String, LossyCount>();
    private ConcurrentHashMap<String, StreamEvent> map = new ConcurrentHashMap<String, StreamEvent>();
    private VariableExpressionExecutor[] variableExpressionExecutors;

    private int totalCount = 0;
    private double currentBucketId=1;
    private double support;           // these will be initialize during init
    private double error;             // these will be initialize during init
    private double windowWidth;

    @Override
    protected void init(ExpressionExecutor[] attributeExpressionExecutors, ExecutionPlanContext executionPlanContext) {
        support = Double.parseDouble(String.valueOf(((ConstantExpressionExecutor) attributeExpressionExecutors[0]).getValue()));
        if (attributeExpressionExecutors.length > 1) {
            error =  Double.parseDouble(String.valueOf(((ConstantExpressionExecutor) attributeExpressionExecutors[1]).getValue()));
        } else {
            error = support / 10; // recommended error is 10% of 20$ of support value;
        }
        if ((support > 1 || support < 0) || (error > 1 || error < 0)) {
            log.error("Wrong argument has provided, Error executing the window");
        }
        variableExpressionExecutors = new VariableExpressionExecutor[attributeExpressionExecutors.length - 2];
        if (attributeExpressionExecutors.length > 2) {  // by-default all the attributes will be compared
            for (int i = 2; i < attributeExpressionExecutors.length; i++) {
                variableExpressionExecutors[i - 2] = (VariableExpressionExecutor) attributeExpressionExecutors[i];
            }
        }
        windowWidth = Math.ceil(1 / error);
        currentBucketId = 1;
    }

    @Override
    protected void process(ComplexEventChunk<StreamEvent> streamEventChunk, Processor nextProcessor, StreamEventCloner streamEventCloner) {
        ComplexEventChunk<StreamEvent> complexEventChunk = new ComplexEventChunk<StreamEvent>();

        StreamEvent streamEvent = streamEventChunk.getFirst();

        while (streamEvent != null) {
            StreamEvent next = streamEvent.getNext();
            streamEvent.setNext(null);

            StreamEvent clonedEvent = streamEventCloner.copyStreamEvent(streamEvent);
            clonedEvent.setType(StreamEvent.Type.EXPIRED);

            totalCount++;
            if (totalCount != 1) {
                currentBucketId = Math.ceil(totalCount / windowWidth);
            }

            StreamEvent oldEvent = map.put(generateKey(streamEvent), clonedEvent);
            if (oldEvent != null) {    // this event is already in the store
                countMap.put(generateKey(streamEvent), countMap.get(generateKey(streamEvent)).incrementCount());
            } else {
                //  This is a new event
                LossyCount lCount;
                lCount = new LossyCount(1, (int)currentBucketId - 1 );
                countMap.put(generateKey(streamEvent), lCount);
            }
            // calculating all the events in the system which match the
            // requirement provided by the user
            List<String> keys = new ArrayList<String>();
            keys.addAll(countMap.keySet());
            for (String key : keys) {
                LossyCount lossyCount = countMap.get(key);
                if (lossyCount.getCount() >= ((support - error) * totalCount)) {
                    // among the selected events, if the newly arrive event is there we mark it as an inEvent
                    if(key.equals(generateKey(streamEvent))) {
                       complexEventChunk.add(streamEvent);
                    }
                }
            }
            if (totalCount % windowWidth == 0) {
                // its time to run the data-structure prune code
                keys = new ArrayList<String>();
                keys.addAll(countMap.keySet());
                for (String key : keys) {
                    LossyCount lossyCount = countMap.get(key);
                    if (lossyCount.getCount() + lossyCount.getBucketId() <= currentBucketId) {
                        log.info("Removing the Event: " + key + " from the window");
                        countMap.remove(key);
                        complexEventChunk.add(map.remove(key));
                    }
                }
            }

            streamEvent = next;
        }
        nextProcessor.process(complexEventChunk);
    }

    @Override
    public void start() {
        //Do nothing
    }

    @Override
    public void stop() {
        //Do nothing
    }

    @Override
    public Object[] currentState() {
        return new Object[]{countMap};
    }

    @Override
    public void restoreState(Object[] state) {
        countMap = (ConcurrentHashMap<String, LossyCount>) state[0];
    }

    private String generateKey(StreamEvent event) {      // for performance reason if its all attribute we don't do the attribute list check
        StringBuilder stringBuilder = new StringBuilder();
        if (variableExpressionExecutors.length == 0) {
            for (Object data : event.getOutputData()) {
                stringBuilder.append(data);
            }
        } else {
            for (VariableExpressionExecutor executor : variableExpressionExecutors) {
                stringBuilder.append(event.getAttribute(executor.getPosition()));
            }
        }
        return stringBuilder.toString();
    }

    @Override
    public StreamEvent find(ComplexEvent matchingEvent, Finder finder) {
        finder.setMatchingEvent(matchingEvent);
        ComplexEventChunk<StreamEvent> returnEventChunk = new ComplexEventChunk<StreamEvent>();
        for(StreamEvent streamEvent:map.values()){
            if (finder.execute(streamEvent)) {
                returnEventChunk.add(streamEventCloner.copyStreamEvent(streamEvent));
            }
        }
        finder.setMatchingEvent(null);
        return returnEventChunk.getFirst();
    }

    @Override
    public Finder constructFinder(Expression expression, MetaComplexEvent metaComplexEvent, ExecutionPlanContext executionPlanContext, List<VariableExpressionExecutor> variableExpressionExecutors, Map<String, EventTable> eventTableMap, int matchingStreamIndex) {
        return SimpleFinderParser.parse(expression, metaComplexEvent, executionPlanContext, variableExpressionExecutors, eventTableMap, matchingStreamIndex, inputDefinition);
    }

    public class LossyCount {
        int count;
        int bucketId;

        public LossyCount(int count, int bucketId) {
            this.count = count;
            this.bucketId = bucketId;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getBucketId() {
            return bucketId;
        }

        public void setBucketId(int bucketId) {
            this.bucketId = bucketId;
        }

        public LossyCount incrementCount(){
            this.count++;
            return this;
        }
    }
}
