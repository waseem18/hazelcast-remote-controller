package com.hazelcast.remotecontroller;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.nio.Address;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class HzCluster {

    private String id = UUID.randomUUID().toString();
    private String version = "CURRENT";
    private String xmlConfig;
    private Config config;

    private final AtomicReference<HazelcastInstance> master = new AtomicReference<>();

    private final ConcurrentHashMap<String, HazelcastInstance> instances = new ConcurrentHashMap<>();

    public HzCluster(String version, String xmlConfig) {
        this.version = version;
        this.xmlConfig = xmlConfig;
        if (xmlConfig != null) {
            InputStream inputStream = new ByteArrayInputStream(xmlConfig.getBytes(StandardCharsets.UTF_8));
            this.config = new XmlConfigBuilder(inputStream).build();
        } else {
            this.config = new XmlConfigBuilder().build();
        }
        //disable multicast
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);
        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember("localhost");

        config.setProperty("hazelcast.tcp.join.port.try.count", "1");

    }

    public String getXmlConfig() {
        return xmlConfig;
    }

    public String getVersion() {
        return version;
    }

    public String getId() {
        return id;
    }

    public Config getConfig() {
        return config;
    }

    public boolean addInstance(String id, HazelcastInstance hzInstance) {
        if (master.compareAndSet(null, hzInstance)) {
            Address address = hzInstance.getCluster().getLocalMember().getAddress();
            String memberAddress = address.getHost() + ":" + address.getPort();
            config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(memberAddress);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setRequiredMember(memberAddress);
        }
        return this.instances.putIfAbsent(id, hzInstance) == null;
    }

    public HazelcastInstance getInstanceById(String id) {
        return this.instances.get(id);
    }

    public void removeInstance(String memberId) {
        this.instances.remove(memberId);
    }

    public Collection<HazelcastInstance> getInstances() {
        return instances.values();
    }

    public void shutdown() {
        for (HazelcastInstance instance : this.instances.values()) {
            instance.getLifecycleService().shutdown();
        }
        this.instances.clear();
    }

    public void terminate() {
        for (HazelcastInstance instance : this.instances.values()) {
            instance.getLifecycleService().terminate();
        }
        this.instances.clear();
    }
}
