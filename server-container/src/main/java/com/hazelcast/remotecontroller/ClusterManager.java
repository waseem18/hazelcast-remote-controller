package com.hazelcast.remotecontroller;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;

public class ClusterManager {

    private static Logger LOG = LogManager.getLogger(Main.class);

    private final ConcurrentHashMap<String, HzCluster> clusterMap = new ConcurrentHashMap<>();

    public ClusterManager() {
    }

    public Cluster createCluster(String hzVersion, String xmlconfig) throws ServerException {
        try {
            HzCluster hzCluster = new HzCluster(hzVersion, xmlconfig);
            this.clusterMap.putIfAbsent(hzCluster.getId(), hzCluster);
            return new Cluster(hzCluster.getId());
        } catch (Exception e) {
            LOG.warn(e);
            throw new ServerException(e.getMessage());
        }
    }

    public HzCluster getCluster(String clusterId) {
        return clusterMap.get(clusterId);
    }

    public Member startMember(String clusterId) throws ServerException {
        LOG.info("Starting a Member on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        if (hzCluster != null) {
            Config config = hzCluster.getConfig();

            LOG.info(config.getNetworkConfig().getJoin().getTcpIpConfig());

            HazelcastInstance hzInstance = Hazelcast.newHazelcastInstance(config);
            com.hazelcast.core.Member member = hzInstance.getCluster().getLocalMember();
            if (hzCluster.addInstance(member.getUuid(), hzInstance)) {
                return new Member(member.getUuid(), member.getAddress().getHost(), member.getAddress().getPort());
            }
        }
        throw new ServerException("Cannot find Cluster with id:" + clusterId);
    }

    public boolean shutdownMember(String clusterId, String memberId) {
        LOG.info("Shutting down the Member " + memberId + "on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        hazelcastInstance.shutdown();
        hzCluster.removeInstance(memberId);
        return true;
    }

    public boolean terminateMember(String clusterId, String memberId) {
        LOG.info("Terminating the Member " + memberId + "on cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        hazelcastInstance.getLifecycleService().terminate();
        hzCluster.removeInstance(memberId);
        return true;
    }

    public boolean suspendMember(String clusterId, String memberId) {
        HzCluster hzCluster = clusterMap.get(clusterId);
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains(hazelcastInstance.getName())) {
                thread.suspend();
            }
        }
        return true;
    }

    public boolean resumeMember(String clusterId, String memberId) {
        HzCluster hzCluster = clusterMap.get(clusterId);
        HazelcastInstance hazelcastInstance = hzCluster.getInstanceById(memberId);
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().contains(hazelcastInstance.getName())) {
                thread.resume();
            }
        }
        return true;
    }

    public boolean shutdownCluster(String clusterId) {
        LOG.info("Shutting down the cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        hzCluster.shutdown();
        this.clusterMap.remove(hzCluster.getId());
        return true;
    }

    public boolean clean() {
        LOG.info("Cleaning the R.C. Cluster Manager");
        shutdownAll();
        clusterMap.clear();
        return true;
    }

    public boolean ping() {
        LOG.info("Ping ... Pong ...");
        return true;
    }

    public boolean shutdownAll() {
        LOG.info("Shutting down the cluster.");
        Hazelcast.shutdownAll();
        return true;
    }

    public boolean terminateCluster(String clusterId) {
        LOG.info("Shutting down the cluster : " + clusterId);
        HzCluster hzCluster = clusterMap.get(clusterId);
        hzCluster.terminate();
        this.clusterMap.remove(hzCluster.getId());
        return true;
    }


}
