package com.merge.merge.build.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "concept_builds")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptBuild {

    @Id
    private UUID id;
    private UUID studentId;
    private UUID conceptId;
    private boolean passed;

    public void setPassed(boolean passed) {
        this.passed = passed;
    }
}
