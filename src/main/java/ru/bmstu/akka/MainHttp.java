package ru.bmstu.akka;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.routing.SmallestMailboxPool;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;

import java.util.concurrent.CompletionStage;

public class MainHttp extends AllDirectives {
    private static ActorRef router;

    public MainHttp(ActorSystem system) {
    }

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create("routes");
        router = system.actorOf(new SmallestMailboxPool(5).props(Props.create(Router.class)), "router");
        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        MainHttp instance = new MainHttp(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow =
                instance.createRoute(system).flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
            routeFlow,
            ConnectHttp.toHost("localhost", 8080),
            materializer
        );
        System.out.println("Server online at http://localhost:8080/\nPress RETURN to stop...");
        System.in.read();
        binding
            .thenCompose(ServerBinding::unbind)
            .thenAccept(unbound -> system.terminate());
    }

    private Route createRoute(ActorSystem system) {
        return route(
                post(() ->
                    entity(Jackson.unmarshaller(PackageDefinition.class), pack -> {
                        router.tell(pack, ActorRef.noSender());
                        return complete("Tests started!");
                    })
                ),
                get(() ->
                    parameter("packageID", (packageID) -> {
                        router.tell(packageID, ActorRef.noSender());
                        return complete("Results sent!");
                    })
                );
    }
}
