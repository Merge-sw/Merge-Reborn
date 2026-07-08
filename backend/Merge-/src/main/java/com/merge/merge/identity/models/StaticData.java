package com.merge.merge.identity.models;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaticData {

    private int yearsOfExperience;
    private PreferredLanguage preferredLanguage;
    private Motivation motivation;

    public StaticData(int yearsOfExperience, PreferredLanguage preferredLanguage, Motivation motivation) {
        this.yearsOfExperience = yearsOfExperience;
        this.preferredLanguage = preferredLanguage;
        this.motivation = motivation;
    }
}
