package com.github.tibor17;

import javax.persistence.*;

import static javax.persistence.AccessType.FIELD;

@Table
@Entity
@Access(FIELD)
public class A {
    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column
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
