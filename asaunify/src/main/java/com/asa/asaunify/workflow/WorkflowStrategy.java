package com.asa.asaunify.workflow;

import com.asa.asaunify.entity.ApprovalStage;
import com.asa.asaunify.entity.Request;
import com.asa.asaunify.enums.StageStatus;

import java.util.List;

public interface WorkflowStrategy {


    List<ApprovalStage> buildStages(Request request);

    void onStageAction(Request request, ApprovalStage stage, StageStatus action);


    boolean isComplete(Request request);
}