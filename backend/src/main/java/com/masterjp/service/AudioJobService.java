package com.masterjp.service;

import com.masterjp.domain.VocabWord;
import com.masterjp.dto.AdminDtos;
import com.masterjp.exception.ApiException;
import com.masterjp.repository.LessonRepository;
import com.masterjp.repository.VocabWordRepository;
import com.masterjp.util.AudioFileUtils;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Regenerate audio (FR-033): job chay nen, khong chan UI.
 * Contract: POST -> {jobId, status}; GET job -> {jobId, status, total, done, failed},
 * status: QUEUED | RUNNING | DONE | FAILED.
 * Cho moc goi edge-tts that duoc danh dau TODO ben duoi.
 */
@Service
public class AudioJobService {

    private static final Logger log = LoggerFactory.getLogger(AudioJobService.class);

    private final VocabWordRepository vocabWordRepository;
    private final LessonRepository lessonRepository;
    private final Executor jobExecutor;
    private final Map<String, AudioJob> jobs = new ConcurrentHashMap<>();

    public AudioJobService(VocabWordRepository vocabWordRepository,
                           LessonRepository lessonRepository,
                           @Qualifier("jobExecutor") Executor jobExecutor) {
        this.vocabWordRepository = vocabWordRepository;
        this.lessonRepository = lessonRepository;
        this.jobExecutor = jobExecutor;
    }

    public AdminDtos.AudioJobResponse regenerate(List<String> wordIds) {
        String jobId = new ObjectId().toString();
        AudioJob job = new AudioJob(jobId, wordIds);
        jobs.put(jobId, job);
        jobExecutor.execute(() -> run(job));
        return new AdminDtos.AudioJobResponse(jobId, job.getStatus());
    }

    public AdminDtos.AudioJobDetailDto getJob(String jobId) {
        AudioJob job = jobs.get(jobId);
        if (job == null) {
            throw new ApiException("JOB_NOT_FOUND",
                    "Không tìm thấy job audio.", HttpStatus.NOT_FOUND);
        }
        return new AdminDtos.AudioJobDetailDto(job.getJobId(), job.getStatus(),
                job.getTotal(), job.getDone(), job.getFailed());
    }

    private void run(AudioJob job) {
        job.setStatus("RUNNING");
        try {
            for (String wordId : job.getWordIds()) {
                try {
                    VocabWord word = vocabWordRepository.findById(wordId).orElse(null);
                    if (word == null) {
                        job.itemDone(wordId, "FAILED", "Từ không tồn tại.");
                        continue;
                    }
                    // TODO(audio): goi edge-tts that tai day —
                    //   edge-tts --voice ja-JP-NanamiNeural --text "<kana/kanji>" \
                    //     --write-media static/audio/{audioFile}
                    // (audioFile deterministic: b{lessonNo:02d}-{orderInLesson:03d}.mp3)
                    // Hien tai: dam bao audioUrl tro dung file static.
                    if (word.getAudioUrl() == null || word.getAudioUrl().isBlank()) {
                        lessonRepository.findById(word.getLessonId()).ifPresent(lesson -> {
                            word.setAudioUrl(AudioFileUtils.audioUrl(
                                    lesson.getLessonNo(), word.getOrderInLesson()));
                            vocabWordRepository.save(word);
                        });
                    }
                    job.itemDone(wordId, "DONE", null);
                } catch (Exception e) {
                    log.warn("Regenerate audio that bai cho word {}", wordId, e);
                    job.itemDone(wordId, "FAILED", e.getMessage());
                }
            }
            job.setStatus("DONE");
        } catch (Exception e) {
            log.error("Audio job {} loi nghiem trong", job.getJobId(), e);
            job.setStatus("FAILED");
        }
    }
}
