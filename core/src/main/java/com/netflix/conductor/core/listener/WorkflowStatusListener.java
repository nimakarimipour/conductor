/*
 * Copyright 2022 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.listener;

import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.NullUnmarked;

/** Listener for the completed and terminated workflows */
public interface WorkflowStatusListener {

    @NullUnmarked default void onWorkflowCompletedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowCompleted(workflow);
        }
    }

    @NullUnmarked default void onWorkflowTerminatedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowTerminated(workflow);
        }
    }

    @NullUnmarked default void onWorkflowFinalizedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowFinalized(workflow);
        }
    }

    void onWorkflowCompleted(WorkflowModel workflow);

    void onWorkflowTerminated(WorkflowModel workflow);

    default void onWorkflowFinalized(WorkflowModel workflow) {}
}
