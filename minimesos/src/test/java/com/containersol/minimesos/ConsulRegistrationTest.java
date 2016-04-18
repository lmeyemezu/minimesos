package com.containersol.minimesos;

import com.containersol.minimesos.config.ConsulConfig;
import com.containersol.minimesos.config.RegistratorConfig;
import com.containersol.minimesos.docker.DockerClientFactory;
import com.containersol.minimesos.docker.DockerContainersUtil;
import com.containersol.minimesos.junit.MesosClusterTestRule;
import com.containersol.minimesos.marathon.MarathonContainer;
import com.containersol.minimesos.mesos.ClusterArchitecture;
import com.containersol.minimesos.mesos.ConsulContainer;
import com.containersol.minimesos.mesos.MesosAgentContainer;
import com.containersol.minimesos.mesos.RegistratorContainer;
import com.github.dockerjava.api.DockerClient;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static com.jayway.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ConsulRegistrationTest {

    protected static final DockerClient dockerClient = DockerClientFactory.getDockerClient();

    // using Marathon slows down destruction of the cluster
    protected static final ClusterArchitecture CONFIG = new ClusterArchitecture.Builder()
            .withZooKeeper()
            .withMaster()
            .withAgent(MesosAgentContainer::new)
            .withMarathon(MarathonContainer::new)
            .withConsul(new ConsulContainer(new ConsulConfig()))
            .withRegistrator(consul -> new RegistratorContainer(consul, new RegistratorConfig()))
            .build();

    @ClassRule
    public static final MesosClusterTestRule CLUSTER = new MesosClusterTestRule(CONFIG);

    @After
    public void after() {
        DockerContainersUtil util = new DockerContainersUtil();
        util.getContainers(false).filterByName(HelloWorldContainer.CONTAINER_NAME_PATTERN).kill().remove();
    }

    @Test
    public void testRegisterServiceWithConsul() {

        CLUSTER.addAndStartProcess(new HelloWorldContainer());

        String ipAddress = DockerContainersUtil.getIpAddress(CLUSTER.getConsul().getContainerId());
        String url = String.format("http://%s:%d/v1/catalog/service/%s",
				   ipAddress, ConsulConfig.CONSUL_HTTP_PORT, HelloWorldContainer.SERVICE_NAME);

        final JSONArray[] body = new JSONArray[1];

        await("Test container did appear in Registrator").atMost(30, TimeUnit.SECONDS).pollDelay(1, TimeUnit.SECONDS).until(() -> {
            try {
                body[0] = Unirest.get(url).asJson().getBody().getArray();
            } catch (UnirestException e) {
                throw new AssertionError(e);
            }
            assertEquals(1, body[0].length());
        });


        JSONObject service = body[0].getJSONObject(0);
        assertEquals(HelloWorldContainer.SERVICE_PORT, service.getInt("ServicePort"));
    }

    @Test
    public void testConsulShouldBeIgnored() throws UnirestException {
        String ipAddress = DockerContainersUtil.getIpAddress(CLUSTER.getConsul().getContainerId());
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
