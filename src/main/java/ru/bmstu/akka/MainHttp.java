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
import akka.japi.Pair;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import scala.concurrent.Future;

import java.util.concurrent.CompletionStage;

public class MainHttp extends AllDirectives {

    private static final String SYSTEM_NAME = "routes", DOMAIN = "localhost";
    private static int PORT = 8080, TIMEOUT = 5000;

    private ActorRef kernel;

    public MainHttp(ActorSystem system) {
        kernel = system.actorOf(Props.create(Kernel.class));
    }

    public static void main(String[] args) throws Exception {
        ActorSystem system = ActorSystem.create(SYSTEM_NAME);
        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);
        MainHttp instance = new MainHttp(system);
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow =
                instance.createRoute(system).flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(
            routeFlow,
            ConnectHttp.toHost(DOMAIN, PORT),
            materializer
        );
        System.out.printf("Server online at http://%s:%d/%nPress RETURN to stop...%n", DOMAIN, PORT);
        System.in.read();
        binding
            .thenCompose(ServerBinding::unbind)
            .thenAccept(unbound -> system.terminate());
    }

    private Route createRoute(ActorSystem system) {
        return route(
                post(() ->
                    entity(Jackson.unmarshaller(PackageDefinition.class), pack -> {
                        for (TestsDefinition t : pack.getTests()) {
                            Pair<PackageDefinition, TestsDefinition> pair = new Pair<>(pack, t);
                            kernel.tell(pair, ActorRef.noSender());
                        }
                        return complete("Tests started");
                    })
                ),
                get(() ->
                    parameter("packageId", (packageId) -> {
                        Future<Object> result = Patterns.ask(kernel, packageId, TIMEOUT);
                        return completeOKWithFuture(result, Jackson.marshaller());
                    })
                ));
    }
}
