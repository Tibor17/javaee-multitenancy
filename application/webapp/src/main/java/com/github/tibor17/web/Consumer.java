package com.github.tibor17.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.LocalBean;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

@LocalBean
@MessageDriven(name = "MyMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/test"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto_acknowledge")})
public class Consumer implements MessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);

    @Override
    public void onMessage(Message message) {
        try {
            String text = message.getBody(String.class);
            LOG.info(text);
        } catch (JMSException e) {
            LOG.error(e.getLocalizedMessage(), e);
        }
    }
}
