package com.merge.merge.identity.models;

import lombok.Getter;

@Getter
public class PersonalisedData {

    private StaticData staticData;
    private DynamicData dynamicData = new DynamicData();

    public void recordScoutIngestion(StaticData staticData) {
        if (this.staticData != null) {
            throw new IllegalStateException("staticData is written once at Scout ingestion and never updated");
        }
        this.staticData = staticData;
    }
}
