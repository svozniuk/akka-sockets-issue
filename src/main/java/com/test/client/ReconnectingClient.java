package com.test.client;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.protocol.Protocol;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Cancellable;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Procedure;
import scala.concurrent.duration.FiniteDuration;

public class ReconnectingClient extends UntypedActor {

    private final Logger logger = LoggerFactory.getLogger(ReconnectingClient.class);

    private Cancellable reconnectTask;
    private ActorRef server;
    private static final String REMOTE_PATH = "akka.tcp://serverSystem@localhost:2552/user/nopServer";
    private static final long RECONNECT_INTERVAL = 5000;

    @Override
    public void preStart() throws Exception {
        reconnectTask = scheduleReconnect();
        getContext().become(connecting);
        logger.info("Starting up {}", this.getName());
    }

    @Override
    public void postStop() throws Exception {
        if (!reconnectTask.isCancelled()) {
            reconnectTask.cancel();
        }
        super.postStop();
    }

    @Override
    public void onReceive(Object o) throws Exception {

    }

    private Procedure<Object> connecting = new Procedure<Object>() {
        @Override
        public void apply(Object message) throws Exception {
            if (Protocol.RECONNECT_TICK.equals(message)) {
                requestServerRegistration();
            } else if (Protocol.CONFIRM_REGISTRATION.equals(message)) {
                handleRegistrationConfirmation();
            } else {
                logger.info("{} received unexpected message: {}", getName(), message);
                unhandled(message);
            }
        }
    };

    private Procedure<Object> connected = new Procedure<Object>() {
        @Override
        public void apply(Object message) throws Exception {
            if (message instanceof Terminated) {
                handleRemoteTermination((Terminated) message);
            } else {
                logger.info("{} received unexpected message: {}", getName(), message);
                unhandled(message);
            }
        }
    };

    private void handleRegistrationConfirmation() {
        logger.info("{} registration confirmed", this.getName());
        server = getSender();
        getContext().watch(server);
        getContext().become(connected);
        reconnectTask.cancel();
    }

    private void handleRemoteTermination(Terminated message) {
        if (message.getActor().equals(server)) {
            logger.info("{} became unreachable from {}", message.getActor().path(), this.getName());
            logger.info("{} will now try reconnecting", this.getName());
            getContext().unwatch(message.getActor());
            server = null;
            reconnectTask = scheduleReconnect();
            getContext().become(connecting);
        } else {
            logger.warn("{} received a termination message from unknown source; ignoring", this.getName());
            logger.warn("Source: {}", message.getActor());
        }
    }

    private Cancellable scheduleReconnect() {
        return getContext()
                .system()
                .scheduler()
                .schedule(new FiniteDuration(0, TimeUnit.MILLISECONDS),
                        new FiniteDuration(RECONNECT_INTERVAL, TimeUnit.MILLISECONDS), getSelf(),
                        Protocol.RECONNECT_TICK, this.getContext().dispatcher(), null);
    }

    protected void requestServerRegistration() {
        ActorSelection serverSelection = getContext().actorSelection(REMOTE_PATH);
        serverSelection.tell(Protocol.REGISTER, getSelf());
        logger.info("Trying to reconnect to {}", serverSelection);
    }

    protected String getName() {
        return "Reconnecting client";
    }

}
