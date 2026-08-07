package com.tridev.callsecurepro.calls;

import android.content.Context;
import android.telephony.PhoneNumberUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tridev.callsecurepro.data.CallSecureDatabase;
import com.tridev.callsecurepro.data.calls.CallNoteDao;
import com.tridev.callsecurepro.data.calls.CallNoteEntity;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CallNoteRepository {

    private final CallNoteDao callNoteDao;

    public CallNoteRepository(@NonNull Context context) {
        callNoteDao = CallSecureDatabase.getInstance(context.getApplicationContext()).callNoteDao();
    }

    @Nullable
    public CallNoteEntity find(long callLogId) {
        return callNoteDao.findByCallLogId(callLogId);
    }

    @NonNull
    public Map<Long, CallNoteEntity> findForCallIds(@NonNull List<Long> callLogIds) {
        if (callLogIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<CallNoteEntity> entities = callNoteDao.findByCallLogIds(callLogIds);
        Map<Long, CallNoteEntity> result = new HashMap<>();
        for (CallNoteEntity entity : entities) {
            result.put(entity.callLogId, entity);
        }
        return result;
    }

    public void save(
            long callLogId,
            @NonNull String number,
            long callTimestamp,
            @NonNull String noteText,
            long followUpAt,
            boolean followUpDone
    ) {
        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized == null) {
            normalized = "";
        }

        String trimmedNote = noteText.trim();
        if (trimmedNote.isEmpty() && followUpAt <= 0L) {
            callNoteDao.deleteByCallLogId(callLogId);
            return;
        }

        callNoteDao.upsert(new CallNoteEntity(
                callLogId,
                normalized,
                callTimestamp,
                trimmedNote,
                Math.max(0L, followUpAt),
                followUpDone,
                System.currentTimeMillis()
        ));
    }

    public void markFollowUpDone(long callLogId) {
        callNoteDao.markFollowUpDone(callLogId, System.currentTimeMillis());
    }

    public void snoozeFollowUp(long callLogId, long followUpAt) {
        callNoteDao.snoozeFollowUp(
                callLogId,
                Math.max(System.currentTimeMillis(), followUpAt),
                System.currentTimeMillis()
        );
    }
}
