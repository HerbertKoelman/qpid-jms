/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.qpid.jms.provider.amqp.message;

import static org.apache.qpid.jms.provider.amqp.message.AmqpMessageSupport.JMS_MSG_TYPE;
import static org.apache.qpid.jms.provider.amqp.message.AmqpMessageSupport.JMS_OBJECT_MESSAGE;

import java.io.IOException;
import java.io.Serializable;

import javax.jms.JMSException;

import org.apache.qpid.jms.exceptions.JmsExceptionSupport;
import org.apache.qpid.jms.message.facade.JmsObjectMessageFacade;
import org.apache.qpid.jms.provider.amqp.AmqpConnection;
import org.apache.qpid.jms.provider.amqp.AmqpConsumer;
import org.apache.qpid.proton.message.Message;

/**
 * Wrapper around an AMQP Message instance that will be treated as a JMS ObjectMessage
 * type.
 */
public class AmqpJmsObjectMessageFacade extends AmqpJmsMessageFacade implements JmsObjectMessageFacade {

    private AmqpObjectTypeDelegate delegate;

    /**
     * Creates a new facade instance
     *
     * @param connection
     *        the AmqpConnection that under which this facade was created.
     */
    public AmqpJmsObjectMessageFacade(AmqpConnection connection) {
        super(connection);
        setAnnotation(JMS_MSG_TYPE, JMS_OBJECT_MESSAGE);

        // TODO Implement Connection property to control default serialization type
        initDelegate(false);
    }

    /**
     * Creates a new Facade around an incoming AMQP Message for dispatch to the
     * JMS Consumer instance.
     *
     * @param consumer
     *        the consumer that received this message.
     * @param message
     *        the incoming Message instance that is being wrapped.
     */
    public AmqpJmsObjectMessageFacade(AmqpConsumer consumer, Message message) {
        super(consumer, message);

        // TODO detect the content type and init the proper delegate.
        initDelegate(false);
    }

    /**
     * @return the appropriate byte value that indicates the type of message this is.
     */
    @Override
    public byte getJmsMsgType() {
        return JMS_OBJECT_MESSAGE;
    }

    @Override
    public boolean isEmpty() {
        // TODO - If null body changes to empty AmqpValue this needs to also change.
        return getAmqpMessage().getBody() == null;
    }

    public boolean isAmqpTypedEncoding() {
        return this.delegate instanceof AmqpObjectTypeDelegate;
    }

    @Override
    public AmqpJmsObjectMessageFacade copy() throws JMSException {
        AmqpJmsObjectMessageFacade copy = new AmqpJmsObjectMessageFacade(connection);
        copyInto(copy);

        try {
            copy.setObject(getObject());
        } catch (Exception e) {
            throw JmsExceptionSupport.create("Failed to copy object value", e);
        }

        return copy;
    }

    @Override
    public Serializable getObject() throws IOException, ClassNotFoundException {
        return delegate.getObject();
    }

    @Override
    public void setObject(Serializable value) throws IOException {
        delegate.setObject(value);
    }

    @Override
    public void clearBody() {
        try {
            setObject(null);
        } catch (IOException e) {
        }
    }

    @Override
    public void onSend() {
        delegate.onSend();
    }

    void setUseAmqpTypedEncoding(boolean useAmqpTypedEncoding) throws JMSException {
        if (useAmqpTypedEncoding && delegate instanceof AmqpSerializedObjectDelegate) {
            AmqpTypedObjectDelegate newDelegate = new AmqpTypedObjectDelegate(message);
            try {
                newDelegate.setObject(delegate.getObject());
            } catch (ClassNotFoundException | IOException e) {
                throw JmsExceptionSupport.create(e);
            }
            delegate = newDelegate;
        } else if (delegate instanceof AmqpTypedObjectDelegate) {
            AmqpSerializedObjectDelegate newDelegate = new AmqpSerializedObjectDelegate(message);
            try {
                newDelegate.setObject(delegate.getObject());
            } catch (ClassNotFoundException | IOException e) {
                throw JmsExceptionSupport.create(e);
            }
            delegate = newDelegate;
        }
    }

    private void initDelegate(boolean useAmqpTypes) {
        if (!useAmqpTypes) {
            delegate = new AmqpSerializedObjectDelegate(getAmqpMessage());
        } else {
            delegate = new AmqpTypedObjectDelegate(getAmqpMessage());
        }
    }
}
