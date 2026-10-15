package com.masterjp.service;

import com.masterjp.domain.VocabWord;
import com.masterjp.dto.WordDtos;

/** Map entity VocabWord -> DTO dung chung cho nhieu service. */
public final class WordMapper {

    private WordMapper() {
    }

    public static WordDtos.WordDto toDto(VocabWord w, String studyStatus) {
        return new WordDtos.WordDto(
                w.getId(), w.getLessonId(), w.getSection().name(),
                w.getKana(), w.getKanji(), w.getRomaji(), w.getMeaningVi(),
                w.getPos(), w.getExampleJa(), w.getExampleVi(),
                w.getAudioUrl(), w.getOrderInLesson(), studyStatus, w.getCluster());
    }

    public static WordDtos.WordSearchDto toSearchDto(VocabWord w, int lessonNo) {
        return new WordDtos.WordSearchDto(
                w.getId(), w.getLessonId(), lessonNo, w.getSection().name(),
                w.getKana(), w.getKanji(), w.getRomaji(), w.getMeaningVi(),
                w.getPos(), w.getExampleJa(), w.getExampleVi(),
                w.getAudioUrl(), w.getOrderInLesson());
    }
}
