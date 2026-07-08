package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(collection = "students")
public class Student {

    @Id
    private UUID id;

    private String name;

    private String details;

    private int xp;

    private UUID stageId;

    private boolean internshipEligible;

    public Student(UUID id, String name, String details, UUID stageId) {
        this.id = id;
        this.name = name;
        this.details = details;
        this.stageId = stageId;
    }

    public void addXp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("xp amount must not be negative");
        }
        this.xp += amount;
    }

    public void advanceToStage(UUID stageId) {
        this.stageId = stageId;
    }

    public void grantInternshipEligibility() {
        this.internshipEligible = true;
    }
}
