package com.kidstory.backend.service;

import com.kidstory.backend.dto.StoryDetailResponse;
import com.kidstory.backend.entity.GenerationJob;
import com.kidstory.backend.entity.StoryScene;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Sahne sesleri ve resimleri için ortak ilerleme hesabı: sahnenin son işi ve dosyanın varlığına bakılır. */
final class MediaProgress {

    private MediaProgress() {
    }

    static StoryDetailResponse.Progress of(Map<Long, GenerationJob.Status> latestJobBySceneId, List<StoryScene> list,
                                           Predicate<StoryScene> ready) {
        int done = 0, pending = 0, failed = 0;
        for (StoryScene scene : list) {
            GenerationJob.Status last = latestJobBySceneId.get(scene.getId());
            if (last == GenerationJob.Status.QUEUED || last == GenerationJob.Status.RUNNING) {
                pending++;
            } else if (ready.test(scene)) {
                done++;
            } else if (last == GenerationJob.Status.FAILED) {
                failed++;
            }
        }
        String status;
        if (list.isEmpty() || (done == 0 && pending == 0 && failed == 0)) {
            status = "NONE";
        } else if (pending > 0) {
            status = "PREPARING";
        } else if (failed > 0) {
            status = done > 0 ? "PARTIAL" : "FAILED";
        } else {
            status = done == list.size() ? "READY" : "PARTIAL";
        }
        return new StoryDetailResponse.Progress(status, done, list.size());
    }
}
