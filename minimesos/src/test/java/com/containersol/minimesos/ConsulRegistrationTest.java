package com.containersol.minimesos;

import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.config.ConsulConfig;
import com.containersol.minimesos.config.RegistratorConfig;
import com.containersol.minimesos.docker.DockerContainersUtil;
import com.containersol.minimesos.marathon.Marathon;
import com.containersol.minimesos.mesos.*;
import com.github.dockerjava.api.DockerClient;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ConsulRegistrationTest {

    protected static final DockerClient dockerClient = DockerClientFactory.build();
    protected static final ClusterArchitecture CONFIG = new ClusterArchitecture.Builder(dockerClient)
            .withZooKeeper()
            .withMaster()
            .withAgent(zooKeeper -> new MesosAgent(dockerClient, zooKeeper))
            .withMarathon(zooKeeper -> new Marathon(dockerClient, zooKeeper))
            .withConsul(new Consul(dockerClient, new ConsulConfig()))
            .withRegistrator(consul -> new Registrator(dockerClient, consul, new RegistratorConfig()))
            .build();

    @ClassRule
    public static final MesosCluster CLUSTER = new MesosCluster(CONFIG);

    @After
    public void after() {
        DockerContainersUtil util = new DockerContainersUtil(CONFIG.dockerClient);
        util.getContainers(false).filterByName(HelloWorldContainer.CONTAINER_NAME_PATTERN).kill().remove();
    }

    @Test
    public void testRegisterServiceWithConsul() throws UnirestException {
        CLUSTER.addAndStartContainer(new HelloWorldContainer(dockerClient));
        String ipAddress = DockerContainersUtil.getIpAddress(dockerClient, CLUSTER.getConsulContainer().getContainerId());
        String url = String.format("http://%s:%d/v1/catalog/service/%s",
                ipAddress, ConsulConfig.CONSUL_HTTP_PORT, HelloWorldContainer.SERVICE_NAME);

        JSONArray body = Unirest.get(url).asJson().getBody().getArray();
        assertEquals(1, body.length());

        JSONObject service = body.getJSONObject(0);
        assertEquals(HelloWorldContainer.SERVICE_PORT, service.getInt("ServicePort"));
    }

    @Test
    public void testConsulShouldBeIgnored() throws UnirestException {
        String ipAddress = DockerContainersUtil.getIpAddress(dockerClient, CLUSTER.getConsulContainer().getContainerId());
        String url = String.format("http://%s:%d/v1/catalog/services", ipAddress, ConsulConfig.CONSUL_HTTP_PORT);

        JSONArray body = Unirest.get(url).asJson().getBody().getArray();
        assertEquals(1, body.length());

        JSONObject service = body.getJSONObject(0);
        assertFalse(service.has("consul-server-8300"));
        assertFalse(service.has("consul-server-8301"));
        assertFalse(service.has("consul-server-8302"));
        assertFalse(service.has("consul-server-8400"));
        assertFalse(service.has("consul-server-8500"));
        assertFalse(service.has("consul-server-8600"));
    }

}