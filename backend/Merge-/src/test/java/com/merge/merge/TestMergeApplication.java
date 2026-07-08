package com.merge.merge;

import org.springframework.boot.SpringApplication;

public class TestMergeApplication {

    public static void main(String[] args) {
        SpringApplication.from(MergeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
