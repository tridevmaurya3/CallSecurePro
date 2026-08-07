package com.tridev.callsecurepro.ui.dial;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.tridev.callsecurepro.R;
import com.tridev.callsecurepro.databinding.FragmentDialBinding;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DialFragment extends Fragment {

    private FragmentDialBinding binding;
    private ExecutorService contactLookupExecutor;
    private final AtomicInteger lookupGeneration = new AtomicInteger();
    private boolean formattingNumber;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentDialBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        contactLookupExecutor = Executors.newSingleThreadExecutor();

        setupNumberInput();
        setupKeypad();
        setupActions();
        refreshContactPermissionState();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            refreshContactPermissionState();
            lookupContactMatch(getDialableNumber());
        }
    }

    private void setupNumberInput() {
        binding.dialNumberInput.setShowSoftInputOnFocus(false);
        binding.dialNumberInput.setSelection(
                binding.dialNumberInput.length()
        );

        binding.dialNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action required.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Formatting is applied after the edit completes.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (formattingNumber || binding == null) {
                    return;
                }

                String current = editable == null ? "" : editable.toString();
                String formatted = formatPhoneNumber(current);

                if (!formatted.equals(current)) {
                    formattingNumber = true;
                    binding.dialNumberInput.setText(formatted);
                    binding.dialNumberInput.setSelection(formatted.length());
                    formattingNumber = false;
                }

                binding.dialNumberInputLayout.setError(null);
                lookupContactMatch(getDialableNumber());
            }
        });
    }

    private void setupKeypad() {
        binding.key1.setOnClickListener(view -> appendDialCharacter("1"));
        binding.key2.setOnClickListener(view -> appendDialCharacter("2"));
        binding.key3.setOnClickListener(view -> appendDialCharacter("3"));
        binding.key4.setOnClickListener(view -> appendDialCharacter("4"));
        binding.key5.setOnClickListener(view -> appendDialCharacter("5"));
        binding.key6.setOnClickListener(view -> appendDialCharacter("6"));
        binding.key7.setOnClickListener(view -> appendDialCharacter("7"));
        binding.key8.setOnClickListener(view -> appendDialCharacter("8"));
        binding.key9.setOnClickListener(view -> appendDialCharacter("9"));
        binding.keyStar.setOnClickListener(view -> appendDialCharacter("*"));
        binding.key0.setOnClickListener(view -> appendDialCharacter("0"));
        binding.keyHash.setOnClickListener(view -> appendDialCharacter("#"));

        binding.key0.setOnLongClickListener(view -> {
            String number = getDialableNumber();
            if (number.isEmpty()) {
                appendDialCharacter("+");
            }
            return true;
        });
    }

    private void setupActions() {
        binding.clearButton.setOnClickListener(view -> {
            binding.dialNumberInput.setText("");
            hideContactMatch();
        });

        binding.backspaceButton.setOnClickListener(view -> deleteLastCharacter());
        binding.backspaceButton.setOnLongClickListener(view -> {
            binding.dialNumberInput.setText("");
            hideContactMatch();
            return true;
        });

        binding.callButton.setOnClickListener(view -> placeCall());
    }

    private void appendDialCharacter(@NonNull String character) {
        String current = getDialableNumber();
        String updated = current + character;
        setDialNumber(updated);
    }

    private void deleteLastCharacter() {
        String current = getDialableNumber();
        if (current.isEmpty()) {
            return;
        }

        String updated = current.substring(0, current.length() - 1);
        setDialNumber(updated);
    }

    private void setDialNumber(@NonNull String number) {
        String formatted = formatPhoneNumber(number);
        formattingNumber = true;
        binding.dialNumberInput.setText(formatted);
        binding.dialNumberInput.setSelection(formatted.length());
        formattingNumber = false;
        binding.dialNumberInputLayout.setError(null);
        lookupContactMatch(getDialableNumber());
    }

    @NonNull
    private String getDialableNumber() {
        if (binding == null || binding.dialNumberInput.getText() == null) {
            return "";
        }

        return PhoneNumberUtils.stripSeparators(
                binding.dialNumberInput.getText().toString().trim()
        );
    }

    @NonNull
    private String formatPhoneNumber(@NonNull String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String dialable = PhoneNumberUtils.stripSeparators(trimmed);
        if (dialable.contains("*") || dialable.contains("#")) {
            return dialable;
        }

        String countryIso = Locale.getDefault().getCountry();
        String formatted = PhoneNumberUtils.formatNumber(dialable, countryIso);
        return formatted == null || formatted.trim().isEmpty() ? dialable : formatted;
    }

    private void refreshContactPermissionState() {
        boolean contactsAllowed = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED;

        binding.contactPermissionNote.setVisibility(
                contactsAllowed ? View.GONE : View.VISIBLE
        );

        if (!contactsAllowed) {
            hideContactMatch();
        }
    }

    private void lookupContactMatch(@NonNull String number) {
        if (binding == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
        ) != PackageManager.PERMISSION_GRANTED) {
            hideContactMatch();
            return;
        }

        String normalized = PhoneNumberUtils.normalizeNumber(number);
        if (normalized.length() < 3 || contactLookupExecutor == null
                || contactLookupExecutor.isShutdown()) {
            hideContactMatch();
            return;
        }

        int generation = lookupGeneration.incrementAndGet();

        contactLookupExecutor.execute(() -> {
            ContactMatch match = queryContactMatch(number);

            if (!isAdded() || generation != lookupGeneration.get()) {
                return;
            }

            requireActivity().runOnUiThread(() -> {
                if (binding == null || generation != lookupGeneration.get()) {
                    return;
                }

                if (match == null) {
                    hideContactMatch();
                } else {
                    showContactMatch(match);
                }
            });
        });
    }

    @Nullable
    private ContactMatch queryContactMatch(@NonNull String number) {
        Uri lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
        );

        String[] projection = new String[]{
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.NUMBER
        };

        try (Cursor cursor = requireContext().getContentResolver().query(
                lookupUri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }

            int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER);

            String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
            String matchedNumber = numberIndex >= 0 ? cursor.getString(numberIndex) : number;

            if (name == null || name.trim().isEmpty()) {
                return null;
            }

            return new ContactMatch(
                    name.trim(),
                    matchedNumber == null ? number : matchedNumber.trim()
            );
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private void showContactMatch(@NonNull ContactMatch match) {
        binding.contactMatchCard.setVisibility(View.VISIBLE);
        binding.contactMatchName.setText(match.name);
        binding.contactMatchNumber.setText(match.number);

        String trimmed = match.name.trim();
        String initial = trimmed.isEmpty()
                ? "#"
                : trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
        binding.contactMatchInitial.setText(initial);
    }

    private void hideContactMatch() {
        if (binding != null) {
            binding.contactMatchCard.setVisibility(View.GONE);
        }
    }

    private void placeCall() {
        String number = getDialableNumber();
        if (number.isEmpty()) {
            binding.dialNumberInputLayout.setError(getString(R.string.dial_empty_number));
            binding.dialNumberInput.requestFocus();
            return;
        }

        Uri callUri = Uri.fromParts("tel", number, null);
        boolean directCallAllowed = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED;

        if (directCallAllowed) {
            Intent directCallIntent = new Intent(Intent.ACTION_CALL, callUri);
            try {
                startActivity(directCallIntent);
                return;
            } catch (SecurityException | ActivityNotFoundException ignored) {
                // Fall back to the system dialer below.
            }
        }

        Intent dialIntent = new Intent(Intent.ACTION_DIAL, callUri);
        try {
            startActivity(dialIntent);
            if (!directCallAllowed) {
                Toast.makeText(
                        requireContext(),
                        R.string.dial_call_permission_fallback,
                        Toast.LENGTH_SHORT
                ).show();
            }
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(
                    requireContext(),
                    R.string.dial_no_phone_app,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onDestroyView() {
        lookupGeneration.incrementAndGet();
        if (contactLookupExecutor != null) {
            contactLookupExecutor.shutdownNow();
            contactLookupExecutor = null;
        }
        binding = null;
        super.onDestroyView();
    }

    private static final class ContactMatch {
        @NonNull
        private final String name;
        @NonNull
        private final String number;

        private ContactMatch(@NonNull String name, @NonNull String number) {
            this.name = name;
            this.number = number;
        }
    }
}
