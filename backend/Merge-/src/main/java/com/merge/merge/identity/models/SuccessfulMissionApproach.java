package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuccessfulMissionApproach {

    private UUID conceptId;
    private String approach;

    public SuccessfulMissionApproach(UUID conceptId, String approach) {
        this.conceptId = conceptId;
        this.approach = approach;
    }
}
