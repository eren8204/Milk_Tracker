package com.lexcorp.milktracker;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    private TextInputEditText rateInput;
    private MaterialButton datePickerButton;
    private TextView finalAmount, monthYearText;
    private GridView calendarGrid, dayHeadersGrid;
    private CalendarAdapter calendarAdapter;
    private SharedPreferences sharedPreferences;
    private DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private double currentRate = 65.0;
    private int currentMonth, currentYear;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupSharedPreferences();
        setupCurrentDate();
        setupEventListeners();
        setupDayHeaders();
        updateCalendar();
    }

    private void initializeViews() {
        rateInput = findViewById(R.id.rateInput);
        datePickerButton = findViewById(R.id.datePickerButton);
        finalAmount = findViewById(R.id.finalAmount);
        monthYearText = findViewById(R.id.monthYearText);
        calendarGrid = findViewById(R.id.calendarGrid);
        dayHeadersGrid = findViewById(R.id.dayHeadersGrid);
    }

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences("MilkTracker", Context.MODE_PRIVATE);
    }

    private void setupCurrentDate() {
        Calendar calendar = Calendar.getInstance();
        currentMonth = calendar.get(Calendar.MONTH); // 0-11
        currentYear = calendar.get(Calendar.YEAR);

        String savedRate = sharedPreferences.getString("rate", "65");
        rateInput.setText(savedRate);
        currentRate = Double.parseDouble(savedRate);

        updateMonthYearText();
    }

    private void updateMonthYearText() {
        String monthName = new SimpleDateFormat("MMMM", Locale.getDefault())
                .format(getDateForMonthYear());
        monthYearText.setText(monthName + " " + currentYear);
        datePickerButton.setText(new SimpleDateFormat("MMM yyyy", Locale.getDefault())
                .format(getDateForMonthYear()));
    }

    private Date getDateForMonthYear() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, currentYear);
        cal.set(Calendar.MONTH, currentMonth);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return cal.getTime();
    }

    private void setupEventListeners() {
        rateInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveRate();
            }
        });

        datePickerButton.setOnClickListener(v -> showMonthYearPickerDialog());
    }

    private void setupDayHeaders() {
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_day_header, dayNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_day_header, parent, false);
                }
                TextView dayHeader = convertView.findViewById(R.id.dayHeader);
                dayHeader.setText(getItem(position));

                // Highlight Sunday (position 0) with different color
                if (position == 0) {
                    dayHeader.setTextColor(getColor(R.color.primary));
                } else {
                    dayHeader.setTextColor(getColor(R.color.onSurface));
                }

                return convertView;
            }
        };
        dayHeadersGrid.setAdapter(adapter);
    }

    private void showMonthYearPickerDialog() {
        // Create a custom dialog for month/year selection
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_month_year_picker);
        dialog.setTitle("Select Month and Year");

        NumberPicker monthPicker = dialog.findViewById(R.id.monthPicker);
        NumberPicker yearPicker = dialog.findViewById(R.id.yearPicker);
        Button btnOk = dialog.findViewById(R.id.btnOk);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        // Set up month picker
        String[] months = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(11);
        monthPicker.setDisplayedValues(months);
        monthPicker.setValue(currentMonth);

        // Set up year picker (current year - 10 to current year + 1)
        yearPicker.setMinValue(currentYear - 10);
        yearPicker.setMaxValue(currentYear + 50);
        yearPicker.setValue(currentYear);

        btnOk.setOnClickListener(v -> {
            currentMonth = monthPicker.getValue();
            currentYear = yearPicker.getValue();
            updateMonthYearText();
            updateCalendar();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void updateCalendar() {
        calendarAdapter = new CalendarAdapter();
        calendarGrid.setAdapter(calendarAdapter);
        updateTotalAmount();
    }

    private void saveRate() {
        String rateText = Objects.requireNonNull(rateInput.getText()).toString();
        if (!TextUtils.isEmpty(rateText)) {
            try {
                currentRate = Double.parseDouble(rateText);
                sharedPreferences.edit().putString("rate", rateText).apply();
                updateCalendar();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid rate format", Toast.LENGTH_SHORT).show();
                rateInput.setText("65");
                currentRate = 65.0;
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateTotalAmount() {
        double total = 0;
        String monthKey = getMonthKey();

        try {
            String monthData = sharedPreferences.getString(monthKey, "{}");
            JSONObject jsonObject = new JSONObject(monthData);

            for (int i = 1; i <= getDaysInMonth(); i++) {
                if (jsonObject.has(String.valueOf(i))) {
                    total += jsonObject.getDouble(String.valueOf(i));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        finalAmount.setText("₹" + decimalFormat.format(total));
    }

    private String getMonthKey() {
        return currentMonth + "_" + currentYear;
    }

    private int getDaysInMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, currentYear);
        calendar.set(Calendar.MONTH, currentMonth);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private int getFirstDayOfWeek() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, currentYear);
        calendar.set(Calendar.MONTH, currentMonth);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        return calendar.get(Calendar.DAY_OF_WEEK) - 1; // Sunday = 0, Monday = 1, etc.
    }

    private class CalendarAdapter extends BaseAdapter {
        private final List<Integer> days;
        private final int firstDayOfWeek;
        private final int totalDays;

        public CalendarAdapter() {
            totalDays = getDaysInMonth();
            firstDayOfWeek = getFirstDayOfWeek();
            days = new ArrayList<>();

            // Add empty cells for days before the first day of month
            for (int i = 0; i < firstDayOfWeek; i++) {
                days.add(0); // 0 represents empty cell
            }

            // Add actual days
            for (int i = 1; i <= totalDays; i++) {
                days.add(i);
            }
        }

        @Override
        public int getCount() {
            // Always show 42 cells (6 weeks) for consistent grid
            return 42;
        }

        @Override
        public Object getItem(int position) {
            if (position < days.size()) {
                return days.get(position);
            }
            return 0;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_day, parent, false);
            }

            int day = (int) getItem(position);
            ViewHolder holder = new ViewHolder(convertView);

            if (day > 0 && position < days.size()) {
                holder.bind(day);
            } else {
                holder.bindEmpty();
            }

            return convertView;
        }
    }

    private class ViewHolder {
        private final MaterialCardView dayCard;
        private final TextView dayNumber;
        private final TextView dayAmount;
        private final ImageView checkIcon;
        private int day;

        public ViewHolder(View view) {
            dayCard = view.findViewById(R.id.dayCard);
            dayNumber = view.findViewById(R.id.dayNumber);
            dayAmount = view.findViewById(R.id.dayAmount);
            checkIcon = view.findViewById(R.id.checkIcon);
        }

        @SuppressLint("SetTextI18n")
        public void bind(int day) {
            this.day = day;
            dayNumber.setText(String.valueOf(day));
            dayNumber.setVisibility(View.VISIBLE);
            dayAmount.setVisibility(View.VISIBLE);
            dayCard.setEnabled(true);
            dayCard.setClickable(true);

            // Check if milk was purchased this day
            String monthKey = getMonthKey();
            String monthData = sharedPreferences.getString(monthKey, "{}");

            try {
                JSONObject jsonObject = new JSONObject(monthData);
                if (jsonObject.has(String.valueOf(day))) {
                    double amount = jsonObject.getDouble(String.valueOf(day));
                    dayAmount.setText("₹" + decimalFormat.format(amount));
                    dayCard.setCardBackgroundColor(getColor(R.color.card_background_selected));
                    checkIcon.setVisibility(View.VISIBLE);
                } else {
                    dayAmount.setText("₹0");
                    dayCard.setCardBackgroundColor(getColor(R.color.card_background));
                    checkIcon.setVisibility(View.GONE);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            setupClickListeners();
        }

        public void bindEmpty() {
            dayNumber.setVisibility(View.INVISIBLE);
            dayAmount.setVisibility(View.INVISIBLE);
            checkIcon.setVisibility(View.GONE);
            dayCard.setCardBackgroundColor(getColor(android.R.color.transparent));
            dayCard.setCardElevation(0f);
            dayCard.setEnabled(false);
            dayCard.setClickable(false);
        }

        private void setupClickListeners() {
            // Single click - toggle milk purchase with default quantity
            dayCard.setOnClickListener(v -> toggleMilkPurchase(0.5));

            // Long click - open quantity dialog
            dayCard.setOnLongClickListener(v -> {
                showQuantityDialog();
                return true;
            });
        }

        @SuppressLint("SetTextI18n")
        private void toggleMilkPurchase(double defaultQuantity) {
            String monthKey = getMonthKey();
            String monthData = sharedPreferences.getString(monthKey, "{}");

            try {
                JSONObject jsonObject = new JSONObject(monthData);
                String dayKey = String.valueOf(day);

                if (jsonObject.has(dayKey)) {
                    // Remove milk purchase
                    jsonObject.remove(dayKey);
                    dayAmount.setText("₹0");
                    dayCard.setCardBackgroundColor(getColor(R.color.card_background));
                    checkIcon.setVisibility(View.GONE);
                } else {
                    // Add milk purchase with default quantity
                    double amount = currentRate * defaultQuantity;
                    jsonObject.put(dayKey, amount);
                    dayAmount.setText("₹" + decimalFormat.format(amount));
                    dayCard.setCardBackgroundColor(getColor(R.color.card_background_selected));
                    checkIcon.setVisibility(View.VISIBLE);
                }

                sharedPreferences.edit().putString(monthKey, jsonObject.toString()).apply();
                updateTotalAmount();

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void showQuantityDialog() {
            Dialog dialog = new Dialog(MainActivity.this);
            dialog.setContentView(R.layout.dialog_quantity);
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(android.R.color.transparent);

            EditText quantityInput = dialog.findViewById(R.id.quantityInput);
            Button cancelButton = dialog.findViewById(R.id.cancelButton);
            Button saveButton = dialog.findViewById(R.id.saveButton);

            // Set current quantity if exists
            String monthKey = getMonthKey();
            String monthData = sharedPreferences.getString(monthKey, "{}");

            try {
                JSONObject jsonObject = new JSONObject(monthData);
                if (jsonObject.has(String.valueOf(day))) {
                    double currentAmount = jsonObject.getDouble(String.valueOf(day));
                    double currentQuantity = currentAmount / currentRate;
                    quantityInput.setText(decimalFormat.format(currentQuantity));
                } else {
                    quantityInput.setText("0.5");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                quantityInput.setText("0.5");
            }

            cancelButton.setOnClickListener(v -> dialog.dismiss());

            saveButton.setOnClickListener(v -> {
                String quantityText = quantityInput.getText().toString();
                if (!TextUtils.isEmpty(quantityText)) {
                    try {
                        double quantity = Double.parseDouble(quantityText);
                        if (quantity > 0) {
                            updateMilkQuantity(quantity);
                            dialog.dismiss();
                        } else {
                            Toast.makeText(MainActivity.this, "Quantity must be positive", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(MainActivity.this, "Invalid quantity", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Please enter quantity", Toast.LENGTH_SHORT).show();
                }
            });

            dialog.show();
        }

        @SuppressLint("SetTextI18n")
        private void updateMilkQuantity(double quantity) {
            String monthKey = getMonthKey();
            String monthData = sharedPreferences.getString(monthKey, "{}");

            try {
                JSONObject jsonObject = new JSONObject(monthData);
                double amount = currentRate * quantity;
                jsonObject.put(String.valueOf(day), amount);

                sharedPreferences.edit().putString(monthKey, jsonObject.toString()).apply();

                dayAmount.setText("₹" + decimalFormat.format(amount));
                dayCard.setCardBackgroundColor(getColor(R.color.card_background_selected));
                checkIcon.setVisibility(View.VISIBLE);
                updateTotalAmount();

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}