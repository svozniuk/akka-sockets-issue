package com.test.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.test.protocol.Protocol;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import scala.concurrent.duration.FiniteDuration;

public class NopServer extends UntypedActor {

    private final Logger logger = LoggerFactory.getLogger(NopServer.class);
    private final Map<ActorPath, ActorRef> clients = new HashMap<>();

    @Override
    public void preStart() throws Exception {
        logger.info("Starting up server");
        schedulePeriodicWork();
    }

    @Override
    public void onReceive(Object message) throws Exception {
        if (Protocol.REGISTER.equals(message)) {
            handleClientRegistration();
        } else if (message instanceof Terminated) {
            handleClientTermination((Terminated) message);
        } else if (message instanceof String) {
            logger.info("Server received some work");
        } else {
            handleUnknownMessage(message);
        }
    }

    private void handleClientRegistration() {
        ActorRef client = getSender();
        logger.info("Received client registration {}", client);

        if (clients.get(client.path()) != null) {
            logger.info("A client interested in data is already registered at {}. Replacing with a new one",
                    client.path());
        }

        clients.put(client.path(), client);
        context().watch(client);
        client.tell(Protocol.CONFIRM_REGISTRATION, getSelf());
    }

    private void handleClientTermination(Terminated message) {
        ActorRef terminatedActor = message.getActor();
        ActorPath terminatedActorPath = terminatedActor.path();
        context().unwatch(terminatedActor);
        clients.remove(terminatedActorPath);
        logger.info("Client {} has become unreachable. Unregistering it.", terminatedActor);
    }


    private void handleUnknownMessage(Object message) {
        logger.warn("Received unexpected message {}", message);
        unhandled(message);
    }

    private Cancellable schedulePeriodicWork() {
        return getContext()
                .system()
                .scheduler()
                .schedule(new FiniteDuration(0, TimeUnit.MILLISECONDS),
                        new FiniteDuration(10000, TimeUnit.MILLISECONDS), getSelf(),
                        "Some work", this.getContext().dispatcher(), null);
    }



}
