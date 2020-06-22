package com.bolsadeideas.springboot.webflux.app;

import com.mockrunner.jms.ConfigurationManager;
import com.mockrunner.jms.DestinationManager;
import com.mockrunner.mock.jms.MockQueue;
import com.mockrunner.mock.jms.MockQueueConnectionFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SessionAwareMessageListener;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.UUID;

@RunWith(SpringJUnit4ClassRunner.class)
@Import(JmsMessageWithRealListenerTest.JmsMockConfiguration.class)
public class JmsMessageWithRealListenerTest {

    private static final int MESSAGE_DELIVERY_MODE = 2;
    private static final long MESSAGE_EXPIRATION = 109500000;
    private static final int MESSAGE_PRIORITY = 8;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private MockQueue mockQueue;

    @MockBean
    private SessionAwareMessageListener<TextMessage> messageListener;

    @Captor
    private ArgumentCaptor<TextMessage> textMessageArgumentCaptor;

    @Captor
    private ArgumentCaptor<Session> sessionArgumentCaptor;

    @Test
    public void shouldSendMessage() throws Exception {

        jmsTemplate.send(mockQueue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                TextMessage message = session.createTextMessage();
                message.setText("This is test message from MockRunner " + UUID.randomUUID());
                System.out.println("sending!!!!: " + message);
                message.setJMSDeliveryMode(MESSAGE_DELIVERY_MODE);
                message.setJMSExpiration(MESSAGE_EXPIRATION);
                message.setJMSPriority(MESSAGE_PRIORITY);
                return message;
            }
        });
        Mockito.verify(messageListener, Mockito.timeout(10000).atLeast(1))
                .onMessage(textMessageArgumentCaptor.capture(), sessionArgumentCaptor.capture());
        Message sentMessage = textMessageArgumentCaptor.getValue();

        Assert.assertEquals(MESSAGE_DELIVERY_MODE, sentMessage.getJMSDeliveryMode());
        //Assert.assertEquals(MESSAGE_EXPIRATION, sentMessage.getJMSExpiration());
        Assert.assertEquals(MESSAGE_PRIORITY, sentMessage.getJMSPriority());
    }

    @Configuration
    public static class JmsMockConfiguration {

        @Bean
        public DestinationManager destinationManager() {
            return new DestinationManager();
        }

        @Bean
        public ConfigurationManager configurationManager() {
            return new ConfigurationManager();
        }

        @Bean
        public MockQueue mockQueue(DestinationManager destinationManager) {
            return destinationManager.createQueue("demoMockRunnerQueue");
        }

        @Bean
        public MockQueueConnectionFactory jmsQueueConnectionFactory(
                DestinationManager destinationManager, ConfigurationManager configurationManager) {
            return new MockQueueConnectionFactory(destinationManager, configurationManager);
        }

        @Bean
        public JmsTemplate jmsTemplate(MockQueueConnectionFactory jmsQueueConnectionFactory) {
            return new CustomJmsTemplate(jmsQueueConnectionFactory);
        }

        @Bean
        public SessionAwareMessageListener<TextMessage> messageListener() {
            return new EmployeeMessageListener();
        }

        @Bean
        public DefaultMessageListenerContainer jmsListenerContainer(
                MockQueueConnectionFactory jmsQueueConnectionFactory, SessionAwareMessageListener<TextMessage> messageListener) {
            DefaultMessageListenerContainer dmlc = new DefaultMessageListenerContainer();
            dmlc.setConnectionFactory(jmsQueueConnectionFactory);
            dmlc.setDestinationName("demoMockRunnerQueue");

            // To schedule our concurrent listening tasks
            // dmlc.setTaskExecutor(taskExecutor());

            // To perform actual message processing
            dmlc.setMessageListener(messageListener);

            dmlc.setConcurrentConsumers(20);

            // ... more parameters that you might want to inject ...
            return dmlc;
        }
    }

    public static class EmployeeMessageListener implements SessionAwareMessageListener<TextMessage> {
        @Override
        public void onMessage(TextMessage textMessage, Session session) throws JMSException {
            System.out.println("Message Received " + textMessage.getText());
            System.out.println("textMessage.deliveryMode: " + textMessage.getJMSDeliveryMode());
            System.out.println("textMessage.expiration: " + textMessage.getJMSExpiration());
            System.out.println("textMessage.priority: " + textMessage.getJMSPriority());
        }
    }
}
