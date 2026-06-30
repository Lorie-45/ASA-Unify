package com.asa.asaunify.repos;


import com.asa.asaunify.entity.Request;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.entity.VehicleTripAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleTripAssignmentRepo extends JpaRepository<VehicleTripAssignment, UUID> {

    // Find assignment by its linked request
    Optional<VehicleTripAssignment> findByRequest(Request request);

    // All assignments for a specific driver
    List<VehicleTripAssignment> findByDriverOrderByAssignedAtDesc(User driver);

    // All assignments made by a specific fleet manager
    List<VehicleTripAssignment> findByAssignedByOrderByAssignedAtDesc(User assignedBy);

    // All assignments not yet seen by the driver
    List<VehicleTripAssignment> findByDriverAndSeenAtIsNull(User driver);

    // All assignments already seen by the driver
    List<VehicleTripAssignment> findByDriverAndSeenAtIsNotNull(User driver);

    // Fleet manager view — all unseen assignments across all drivers
    @Query("SELECT v FROM VehicleTripAssignment v WHERE v.seenAt IS NULL " +
            "ORDER BY v.assignedAt DESC")
    List<VehicleTripAssignment> findAllUnseen();

    List<VehicleTripAssignment> findByDriver(User driver);

    // Check if a request already has an assignment
    boolean existsByRequest(Request request);
}