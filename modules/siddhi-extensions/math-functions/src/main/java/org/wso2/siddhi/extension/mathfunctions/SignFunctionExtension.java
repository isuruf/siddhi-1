/*
 * Copyright (c) 2005-2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.siddhi.extension.mathfunctions;

import org.apache.log4j.Logger;
import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.exception.ExecutionPlanCreationException;
import org.wso2.siddhi.core.exception.OperationNotSupportedException;
import org.wso2.siddhi.core.executor.ExpressionExecutor;
import org.wso2.siddhi.core.executor.function.FunctionExecutor;
import org.wso2.siddhi.query.api.definition.Attribute;

/**
 * signum(a);
 * <p/>
 * Returns the sign of a as '1.0' (if a is positive) or '-1.0' (if a is
 * negative), '0.0' otherwise.
 * <p/>
 * Accept Type(s) :DOUBLE/INT/FLOAT/LONG
 * Return Type(s): INT
 * Returning an INT rather than a DOUBLE because an INT is *sufficient* to represent -1,0 and 1
 */
public class SignFunctionExtension extends FunctionExecutor {
    private static final Logger log = Logger.getLogger(SignFunctionExtension.class);
    private boolean isDebugMode = false;
    Attribute.Type returnType = Attribute.Type.INT;

    @Override
    protected void init(ExpressionExecutor[] attributeExpressionExecutors, ExecutionPlanContext executionPlanContext) {
        if (log.isDebugEnabled()) {
            isDebugMode = true;
        }
        if (attributeExpressionExecutors.length != 1) {
            String errMsg = "Invalid no of Arguments Passed. Required 1. Found " + attributeExpressionExecutors.length;
            throw new ExecutionPlanCreationException(errMsg);
        }
        for (ExpressionExecutor expressionExecutor : attributeExpressionExecutors) {
            Attribute.Type attributeType = expressionExecutor.getReturnType();
            if (isDebugMode) {
                log.debug("Attribute Type - " + attributeType.toString());
            }
            if (!((attributeType == Attribute.Type.DOUBLE)
                    || (attributeType == Attribute.Type.INT)
                    || (attributeType == Attribute.Type.FLOAT)
                    || (attributeType == Attribute.Type.LONG))) {
                String errMsg = "Invalid parameter type found - " + attributeType.toString();
                throw new ExecutionPlanCreationException(errMsg);
            }
        }
    }

    @Override
    protected Object execute(Object[] data) {
        return null;  //Since the signum function takes in only 1 parameter, this method does not get called. Hence, not implemented.
    }

    @Override
    protected Object execute(Object data) {
        double inputValue = 0d;
        double returnValue = 0d;

        if (data != null) {
            //type-conversion
            if (data instanceof Integer) {
                int inputInt = (Integer) data;
                inputValue = (double) inputInt;
            } else if (data instanceof Long) {
                long inputLong = (Long) data;
                inputValue = (double) inputLong;
            } else if (data instanceof Float) {
                float inputLong = (Float) data;
                inputValue = (double) inputLong;
            } else if (data instanceof Double) {
                inputValue = (Double) data;
            }

            returnValue = Math.signum(inputValue);
            if (isDebugMode) {
                log.debug("Input Value = " + inputValue + ", Output Value =" + returnValue);
            }
        } else {
            throw new OperationNotSupportedException("Input to the math:signum() function cannot be null");
        }
        return (int) returnValue;
    }

    @Override
    public void start() {
        //Nothing to start.
    }

    @Override
    public void stop() {
        //Nothing to stop.
    }

    @Override
    public Attribute.Type getReturnType() {
        return returnType;
    }

    @Override
    public Object[] currentState() {
        return null;    //No need to maintain state.
    }

    @Override
    public void restoreState(Object[] state) {
        if (log.isDebugEnabled()) {
            isDebugMode = true;
        }
    }
}
