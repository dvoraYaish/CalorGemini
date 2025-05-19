package com.example.calorgemini;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etAge, etWeight, etHeight, etWeightChangeRate;
    private RadioGroup rgGender, rgWeightGoal;
    private Spinner spActivityLevel;
    private Button btnCalculate;
    private TextView tvResult;

    private String selectedActivityLevel;
    private GenerativeModelFutures modelFutures;
    private final Executor executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Gemini API
        initializeGeminiApi();

        // Initialize UI components
        initializeViews();

        // Setup activity level spinner
        setupActivityLevelSpinner();

        // Setup button click listener
        btnCalculate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculateCalorieNeeds();
            }
        });
    }

    private void initializeGeminiApi() {
        // Initialize the GenerativeModel with your API key
        // Using the correct model name according to the latest API version
        GenerativeModel gModel = new GenerativeModel(
                "gemini-1.0-pro", // Updated model name
                getString(R.string.gemini_api_key)
        );
        modelFutures = GenerativeModelFutures.from(gModel);
    }

    private void initializeViews() {
        etAge = findViewById(R.id.etAge);
        etWeight = findViewById(R.id.etWeight);
        etHeight = findViewById(R.id.etHeight);
        etWeightChangeRate = findViewById(R.id.etWeightChangeRate);
        rgGender = findViewById(R.id.rgGender);
        rgWeightGoal = findViewById(R.id.rgWeightGoal);
        spActivityLevel = findViewById(R.id.spActivityLevel);
        btnCalculate = findViewById(R.id.btnCalculate);
        tvResult = findViewById(R.id.tvResult);
    }

    private void setupActivityLevelSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.activity_levels,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spActivityLevel.setAdapter(adapter);

        spActivityLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedActivityLevel = parent.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedActivityLevel = "פעילות מתונה";
            }
        });
    }

    private void calculateCalorieNeeds() {
        // Get user inputs
        String ageStr = etAge.getText().toString();
        String weightStr = etWeight.getText().toString();
        String heightStr = etHeight.getText().toString();
        String weightChangeRateStr = etWeightChangeRate.getText().toString();

        // Validate inputs
        if (ageStr.isEmpty() || weightStr.isEmpty() || heightStr.isEmpty() ||
                weightChangeRateStr.isEmpty()) {
            Toast.makeText(this, "נא למלא את כל השדות", Toast.LENGTH_SHORT).show();
            return;
        }

        int age = Integer.parseInt(ageStr);
        double weight = Double.parseDouble(weightStr);
        double height = Double.parseDouble(heightStr);
        double weightChangeRate = Double.parseDouble(weightChangeRateStr);

        // Get gender
        String gender = rgGender.getCheckedRadioButtonId() == R.id.rbMale ? "זכר" : "נקבה";

        // Get weight goal
        String weightGoal = rgWeightGoal.getCheckedRadioButtonId() == R.id.rbLoseWeight ?
                "ירידה במשקל" : "עלייה במשקל";

        // Create prompt for Gemini API
        String prompt = createGeminiPrompt(age, weight, height, gender,
                selectedActivityLevel, weightGoal, weightChangeRate);

        // Show loading state
        tvResult.setText("מחשב...");

        // Query Gemini API
        queryGeminiApi(prompt);
    }

    private String createGeminiPrompt(int age, double weight, double height,
                                      String gender, String activityLevel,
                                      String weightGoal, double weightChangeRate) {
        return "חשב את צריכת הקלוריות היומית המומלצת עבור אדם בעל המאפיינים הבאים:\n" +
                "- גיל: " + age + " שנים\n" +
                "- משקל: " + weight + " ק\"ג\n" +
                "- גובה: " + height + " ס\"מ\n" +
                "- מגדר: " + gender + "\n" +
                "- רמת פעילות גופנית: " + activityLevel + "\n" +
                "- מטרת משקל: " + weightGoal + "\n" +
                "- קצב " + (weightGoal.equals("ירידה במשקל") ? "ירידה" : "עלייה") +
                " במשקל מבוקש: " + weightChangeRate + " ק\"ג בשבוע\n\n" +
                "אנא חשב:\n" +
                "1. BMR (קצב חילוף חומרים בסיסי) לפי נוסחת Mifflin-St Jeor\n" +
                "2. TDEE (סך הוצאת אנרגיה יומית) בהתבסס על רמת הפעילות\n" +
                "3. צריכת קלוריות יומית מומלצת להשגת המטרה בקצב המבוקש\n" +
                "4. חלוקה מומלצת של מאקרו-נוטריאנטים (חלבונים, פחמימות, שומנים)\n" +
                "5. המלצות תזונתיות בסיסיות להשגת המטרה\n\n" +
                "תן את התשובה בעברית בפורמט תמציתי, ברור ומסודר.";
    }

    private void queryGeminiApi(String prompt) {
        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> response = modelFutures.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseText = result.getText();
                runOnUiThread(() -> {
                    tvResult.setText(responseText);
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    String errorMsg = "שגיאה: " + t.getMessage();
                    // Add more detailed error information
                    if (t.getCause() != null) {
                        errorMsg += "\n\nסיבה: " + t.getCause().getMessage();
                    }
                    tvResult.setText(errorMsg);

                    // Show a simplified error message in the toast
                    String toastMsg = "שגיאה בחיבור ל-Gemini API";
                    if (t.getMessage().contains("models/gemini")) {
                        toastMsg = "שגיאה: מודל Gemini לא נמצא";
                    } else if (t.getMessage().contains("Authentication")) {
                        toastMsg = "שגיאה: מפתח API לא תקין";
                    } else if (t.getMessage().contains("network")) {
                        toastMsg = "שגיאת רשת: אנא בדוק את החיבור לאינטרנט";
                    }
                    Toast.makeText(MainActivity.this, toastMsg, Toast.LENGTH_LONG).show();

                    // Log the error for debugging
                    Log.e("GeminiAPI", "Error calling Gemini API", t);
                });
            }
        }, executor);
    }
}

