package com.masterjp.controller;

import com.masterjp.dto.AdminDtos;
import com.masterjp.dto.CommonDtos;
import com.masterjp.dto.LessonDtos;
import com.masterjp.dto.PaymentDtos;
import com.masterjp.dto.WordDtos;
import com.masterjp.security.UserPrincipal;
import com.masterjp.service.AdminService;
import com.masterjp.service.AudioJobService;
import com.masterjp.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Tat ca endpoint yeu cau ROLE_ADMIN (bao ve o SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AudioJobService audioJobService;
    private final PaymentService paymentService;

    public AdminController(AdminService adminService, AudioJobService audioJobService,
                           PaymentService paymentService) {
        this.adminService = adminService;
        this.audioJobService = audioJobService;
        this.paymentService = paymentService;
    }

    // ---------- Lessons ----------

    @GetMapping("/lessons")
    public List<LessonDtos.LessonDetailDto> listLessons() {
        return adminService.listLessons();
    }

    @PostMapping("/lessons")
    public LessonDtos.LessonDetailDto createLesson(@Valid @RequestBody LessonDtos.CreateLessonRequest req,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.createLesson(principal.getId(), req);
    }

    @PatchMapping("/lessons/{id}")
    public LessonDtos.LessonDetailDto updateLesson(@PathVariable String id,
                                                   @Valid @RequestBody LessonDtos.UpdateLessonRequest req,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.updateLesson(principal.getId(), id, req);
    }

    @DeleteMapping("/lessons/{id}")
    public CommonDtos.MessageResponse deleteLesson(@PathVariable String id,
                                                   @AuthenticationPrincipal UserPrincipal principal) {
        adminService.deleteLesson(principal.getId(), id);
        return new CommonDtos.MessageResponse("Đã xóa bài học (từ vựng được ẩn mềm).");
    }

    // ---------- Words ----------

    @GetMapping("/words")
    public CommonDtos.PageResponse<WordDtos.AdminWordDto> listWords(
            @RequestParam(required = false) String lessonId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        return adminService.listWords(lessonId, q, page);
    }

    @PostMapping("/words")
    public WordDtos.WordDto createWord(@Valid @RequestBody WordDtos.CreateWordRequest req,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.createWord(principal.getId(), req);
    }

    @PatchMapping("/words/{id}")
    public WordDtos.WordDto updateWord(@PathVariable String id,
                                       @Valid @RequestBody WordDtos.UpdateWordRequest req,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.updateWord(principal.getId(), id, req);
    }

    @DeleteMapping("/words/{id}")
    public CommonDtos.MessageResponse deleteWord(@PathVariable String id,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        adminService.deleteWord(principal.getId(), id);
        return new CommonDtos.MessageResponse("Đã ẩn từ vựng (xóa mềm).");
    }

    // ---------- Import Excel (2 buoc: validate -> confirm) ----------

    @PostMapping(value = "/words/import/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WordDtos.ImportValidateResponse validateImport(@RequestParam("file") MultipartFile file) {
        return adminService.validateImport(file);
    }

    @PostMapping("/words/import/confirm")
    public WordDtos.ImportConfirmResponse confirmImport(
            @Valid @RequestBody WordDtos.ImportConfirmRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.confirmImport(principal.getId(), req.rows());
    }

    // ---------- Audio jobs ----------

    @PostMapping("/audio/regenerate")
    public AdminDtos.AudioJobResponse regenerateAudio(
            @Valid @RequestBody WordDtos.RegenerateAudioRequest req) {
        return audioJobService.regenerate(req.wordIds());
    }

    @GetMapping("/audio/jobs/{jobId}")
    public AdminDtos.AudioJobDetailDto getAudioJob(@PathVariable String jobId) {
        return audioJobService.getJob(jobId);
    }

    // ---------- Users & plan ----------

    @GetMapping("/users")
    public CommonDtos.PageResponse<AdminDtos.AdminUserDto> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        return adminService.listUsers(q, page);
    }

    @PatchMapping("/users/{id}/plan")
    public AdminDtos.AdminUserDto updatePlan(@PathVariable String id,
                                             @Valid @RequestBody AdminDtos.UpdatePlanRequest req,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.updatePlan(principal.getId(), id, req);
    }

    // ---------- Stats & audit ----------

    @GetMapping("/stats")
    public AdminDtos.StatsResponse stats() {
        return adminService.stats();
    }

    @GetMapping("/audit-logs")
    public CommonDtos.PageResponse<AdminDtos.AuditLogDto> auditLogs(
            @RequestParam(defaultValue = "0") int page) {
        return adminService.listAuditLogs(page);
    }

    // ---------- Payments (Phase D — F-044, read-only) ----------

    /** Xem hoa don (filter tuy chon), moi nhat truoc. */
    @GetMapping("/payments/invoices")
    public CommonDtos.PageResponse<PaymentDtos.AdminInvoiceDto> listInvoices(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.listInvoicesAdmin(status, userId, page, size);
    }

    /** Xem giao dich SePay (filter tuy chon), moi nhat truoc. */
    @GetMapping("/payments/sepay/transactions")
    public CommonDtos.PageResponse<PaymentDtos.AdminSepayTransactionDto> listSepayTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.listTransactionsAdmin(status, page, size);
    }
}
