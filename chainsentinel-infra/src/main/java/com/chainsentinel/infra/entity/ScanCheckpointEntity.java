package com.chainsentinel.infra.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "scan_checkpoint")
public class ScanCheckpointEntity {

@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

@Column(name = "chain_name", nullable = false, length = 32)
private String chain;

@Column(name = "network", nullable = false, length = 32)
private String network;

@Column(name = "last_scanned_block", nullable = false)
private Long lastScannedBlock;

@Column(name = "updated_at", insertable = false, updatable = false)
private Instant updatedAt;

public Long getId() {
return id;
}

public String getChain() {
return chain;
}

public void setChain(String chain) {
this.chain = chain;
}

public String getNetwork() {
return network;
}

public void setNetwork(String network) {
this.network = network;
}

public Long getLastScannedBlock() {
return lastScannedBlock;
}

public void setLastScannedBlock(Long lastScannedBlock) {
this.lastScannedBlock = lastScannedBlock;
}

public Instant getUpdatedAt() {
return updatedAt;
}
}
