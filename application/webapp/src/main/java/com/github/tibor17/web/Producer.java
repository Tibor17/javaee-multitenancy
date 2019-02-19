package com.github.tibor17.web;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Queue;

public class Producer {
    @Inject
    private JMSContext ctx;

    @Resource(lookup = "java:/jms/queue/test")
    private Queue queue;

    public void send(String msg) {
        ctx.createProducer()
                .send(queue, msg);
    }
}
