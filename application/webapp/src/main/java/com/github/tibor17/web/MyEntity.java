package com.github.tibor17.web;

import javax.persistence.*;

import static javax.persistence.AccessType.FIELD;

@Table(name = "MYTABLE")
@Entity
@Access(FIELD)
public class MyEntity {
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.AUTO)
    private long id;

    @Column(nullable = false)
    private String name;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
