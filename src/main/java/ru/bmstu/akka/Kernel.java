package ru.bmstu.akka;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.OnComplete;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.routing.SmallestMailboxPool;
import scala.concurrent.Future;

public class Kernel extends AbstractActor {
    private final String STORE_ROUTER_NAME = "store", EXECUTE_ROUTER_NAME = "execute";
    private final int NUM_OF_POOLS = 5, TIMEOUT = 5000;

    @Override
    public Receive createReceive() {
        ActorRef storeRouter = getContext().actorOf(new SmallestMailboxPool(NUM_OF_POOLS)
                .props(Props.create(StoreActor.class)), STORE_ROUTER_NAME);
        ActorRef executeRouter = getContext().actorOf(new SmallestMailboxPool(NUM_OF_POOLS)
                .props(Props.create(ExecuteActor.class)), EXECUTE_ROUTER_NAME);
        return ReceiveBuilder.create()
                .match(Pair.class, pair -> {
                    storeRouter.tell(pair, ActorRef.noSender());
                    executeRouter.tell(pair, getSelf());
                })
                .match(String[].class, input -> {
                    storeRouter.tell(input, ActorRef.noSender());
                })
                .match(String.class, packageId -> {
                    Future<Object> result = Patterns.ask(storeRouter, packageId, TIMEOUT);
                    ActorRef mainActor = getSender();
                    result.onComplete(new OnComplete<Object>() {
                        @Override
                        public void onComplete(Throwable failure, Object success) {
                            mainActor.tell(success, ActorRef.noSender());
                        }
                    }, getContext().getDispatcher());
                })
                .build();
    }
}
