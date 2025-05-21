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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
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

    // Gemini cache to reuse results, similar to the example in the presentation
    private Map<String, String> geminiCache = new HashMap<>();

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
        GenerativeModel gModel = new GenerativeModel(
                "gemini-1.5-flash", // Updated model name to match the presentation
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

        // Create prompt for Gemini API using template pattern from the presentation
        String prompt = createGeminiPrompt(age, weight, height, gender,
                selectedActivityLevel, weightGoal, weightChangeRate);

        // Show loading state
        tvResult.setText("מחשב...");

        // Check if we have the result in cache, similar to the presentation example
        if (geminiCache.containsKey(prompt)) {
            // Return cached result if available
            tvResult.setText(geminiCache.get(prompt));
            return;
        }

        // Query Gemini API
        queryGeminiApi(prompt);
    }

    private String createGeminiPrompt(int age, double weight, double height,
                                      String gender, String activityLevel,
                                      String weightGoal, double weightChangeRate) {
        // Using the template pattern with %PLACEHOLDER% from the presentation
        String promptTemplate =
                "חשב את צריכת הקלוריות היומית המומלצת עבור אדם בעל המאפיינים הבאים:\n" +
                        "- גיל: %AGE% שנים\n" +
                        "- משקל: %WEIGHT% ק\"ג\n" +
                        "- גובה: %HEIGHT% ס\"מ\n" +
                        "- מגדר: %GENDER%\n" +
                        "- רמת פעילות גופנית: %ACTIVITY_LEVEL%\n" +
                        "- מטרת משקל: %WEIGHT_GOAL%\n" +
                        "- קצב %CHANGE_DIRECTION% במשקל מבוקש: %WEIGHT_CHANGE_RATE% ק\"ג בשבוע\n\n" +
                        "תן רק את מס הקלוריות ליום שיש לצרוך";

        // Replace placeholders with actual values
        String prompt = promptTemplate
                .replace("%AGE%", String.valueOf(age))
                .replace("%WEIGHT%", String.valueOf(weight))
                .replace("%HEIGHT%", String.valueOf(height))
                .replace("%GENDER%", gender)
                .replace("%ACTIVITY_LEVEL%", activityLevel)
                .replace("%WEIGHT_GOAL%", weightGoal)
                .replace("%WEIGHT_CHANGE_RATE%", String.valueOf(weightChangeRate))
                .replace("%CHANGE_DIRECTION%", weightGoal.equals("ירידה במשקל") ? "ירידה" : "עלייה");

        return prompt;
    }

    // The askGemini function like shown in the presentation
    private void queryGeminiApi(String prompt) {
        Content content = new Content.Builder()
                .addText(prompt)
                .build();

        ListenableFuture<GenerateContentResponse> response = modelFutures.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                final String responseText = result.getText();

                // Cache the result
                geminiCache.put(prompt, responseText);

                runOnUiThread(() -> {
                    tvResult.setText(responseText);
                });
            }

            @Override
            public void onFailure(Throwable t) {
                runOnUiThread(() -> {
                    Log.e("GeminiAPI", "Error calling Gemini API", t);
                    String errorMsg = "שגיאה: " + t.getMessage();
                    tvResult.setText(errorMsg);
                    Toast.makeText(MainActivity.this, "שגיאה בקריאה ל-Gemini API", Toast.LENGTH_LONG).show();
                });
            }
        }, executor);
    }

    // Example of how to handle structured data like in the presentation
    private Map<String, Object> parseNutritionData(String geminiResponse) {
        Map<String, Object> nutritionData = new HashMap<>();

        try {
            // Simple string parsing to extract key values
            if (geminiResponse.contains("BMR:")) {
                String bmrStr = geminiResponse.substring(
                        geminiResponse.indexOf("BMR:") + 4,
                        geminiResponse.indexOf("קלוריות (קצב")
                ).trim();
                nutritionData.put("BMR", Integer.parseInt(bmrStr));
            }

            if (geminiResponse.contains("TDEE:")) {
                String tdeeStr = geminiResponse.substring(
                        geminiResponse.indexOf("TDEE:") + 5,
                        geminiResponse.indexOf("קלוריות (צריכת")
                ).trim();
                nutritionData.put("TDEE", Integer.parseInt(tdeeStr));
            }

            if (geminiResponse.contains("מספר קלוריות יומי:")) {
                String caloriesStr = geminiResponse.substring(
                        geminiResponse.indexOf("מספר קלוריות יומי:") + 18,
                        geminiResponse.indexOf("קלוריות", geminiResponse.indexOf("מספר קלוריות יומי:"))
                ).trim();
                nutritionData.put("DAILY_CALORIES", Integer.parseInt(caloriesStr));
            }

            // Additional parsing for macronutrients could be implemented here

        } catch (Exception e) {
            Log.e("ParsingError", "Error parsing nutrition data", e);
        }

        return nutritionData;
    }
}