package com.capitalone.dashboard.request;

import org.bson.types.ObjectId;

import javax.validation.constraints.NotNull;

/**
 * Created by stevegal on 22/06/2018.
 */
//TODO probably need to specify a max amount
public class LogAnalysisSearchRequest {

    @NotNull
    private ObjectId componentId;

    private Integer max;

    public ObjectId getComponentId() {
        return componentId;
    }

    public void setComponentId(ObjectId componentId) {
        this.componentId = componentId;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }
}