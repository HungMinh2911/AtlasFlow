package com.atlasflow.project.domain;

public class ProjectNameAlreadyExistsException extends RuntimeException {

    private final String name;


    public ProjectNameAlreadyExistsException(String name) {
        super("Project name already exists");
        this.name = name;
    }

    public String name(){
        return name;
    }
}
