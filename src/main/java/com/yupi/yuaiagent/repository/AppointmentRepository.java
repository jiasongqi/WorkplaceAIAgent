package com.yupi.yuaiagent.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yupi.yuaiagent.agent.model.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 预约记录存储
 * 基于文件的持久化存储（可替换为数据库实现）
 *
 * <p>NOTE: @Transactional is NOT applicable here — this is a file-based repository,
 * not backed by a JPA/Spring transaction manager. Concurrency is handled via
 * {@link ReentrantReadWriteLock} instead. If migrated to JPA, add @Transactional
 * to save/update/delete methods.
 * 
 * @author jsq
 */
@Slf4j
@Repository
public class AppointmentRepository {

    @Value("${appointment.storage.dir:./tmp/appointments}")
    private String storageDir;

    private final ObjectMapper objectMapper;
    private final Map<String, Appointment> appointments = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private File storageFile;

    public AppointmentRepository() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        try {
            File dir = new File(storageDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            storageFile = new File(dir, "appointments.json");
            loadFromFile();
            log.info("预约记录存储初始化完成，存储路径：{}", storageFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("初始化预约记录存储失败", e);
        }
    }

    /**
     * 保存预约记录
     */
    public Appointment save(Appointment appointment) {
        lock.writeLock().lock();
        try {
            if (appointment.getAppointmentId() == null || appointment.getAppointmentId().isEmpty()) {
                appointment.setAppointmentId(UUID.randomUUID().toString());
            }
            appointment.setUpdatedAt(LocalDateTime.now());
            if (appointment.getCreatedAt() == null) {
                appointment.setCreatedAt(LocalDateTime.now());
            }
            
            appointments.put(appointment.getAppointmentId(), appointment);
            saveToFile();
            
            log.info("保存预约记录：{}", appointment.getAppointmentId());
            return appointment;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 根据 ID 查找预约
     */
    public Optional<Appointment> findById(String appointmentId) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(appointments.get(appointmentId));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据会话 ID 查找预约
     */
    public List<Appointment> findByChatId(String chatId) {
        lock.readLock().lock();
        try {
            return appointments.values().stream()
                    .filter(a -> chatId.equals(a.getChatId()))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 查找所有预约
     */
    public List<Appointment> findAll() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(appointments.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据状态查找预约
     */
    public List<Appointment> findByStatus(Appointment.AppointmentStatus status) {
        lock.readLock().lock();
        try {
            return appointments.values().stream()
                    .filter(a -> status.equals(a.getStatus()))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 更新预约状态
     */
    public Optional<Appointment> updateStatus(String appointmentId, Appointment.AppointmentStatus status) {
        lock.writeLock().lock();
        try {
            Appointment appointment = appointments.get(appointmentId);
            if (appointment != null) {
                appointment.setStatus(status);
                appointment.setUpdatedAt(LocalDateTime.now());
                saveToFile();
                return Optional.of(appointment);
            }
            return Optional.empty();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 删除预约
     */
    public boolean deleteById(String appointmentId) {
        lock.writeLock().lock();
        try {
            Appointment removed = appointments.remove(appointmentId);
            if (removed != null) {
                saveToFile();
                log.info("删除预约记录：{}", appointmentId);
                return true;
            }
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 从文件加载
     */
    private void loadFromFile() {
        if (storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, Appointment> loaded = objectMapper.readValue(
                        storageFile,
                        new TypeReference<Map<String, Appointment>>() {}
                );
                appointments.putAll(loaded);
                log.info("从文件加载预约记录：{} 条", loaded.size());
            } catch (IOException e) {
                log.error("加载预约记录文件失败", e);
            }
        }
    }

    /**
     * 保存到文件
     */
    private void saveToFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, appointments);
        } catch (IOException e) {
            log.error("保存预约记录文件失败", e);
        }
    }
}
