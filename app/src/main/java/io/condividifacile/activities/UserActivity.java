package io.condividifacile.activities;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.DefaultAxisValueFormatter;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.condividifacile.R;
import io.condividifacile.data.Expense;
import io.condividifacile.services.DownloadIntentService;
import io.condividifacile.services.FirebaseNotificationServices;
import io.condividifacile.utils.ExpandOrCollapse;

public class UserActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static final int RSS_DOWNLOAD_REQUEST_CODE = 1;
    private GoogleApiClient mGoogleApiClient;
    private FirebaseAuth mAuth;
    private FirebaseDatabase database;
    private FirebaseUser currentUser;
    private View header;
    private String email;
    private String name;
    private String uid;
    private String photoUrl;
    private ArrayList<String> groups;
    private LineChart lineChart;
    final ArrayList<Integer> colors = new ArrayList<>();
    final ArrayList<Expense> expenses = new ArrayList<>();
    final ArrayList<String> categories = new ArrayList<>();
    final Map<String, Integer> dates = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.user_drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        Intent notificationService = new Intent(this,FirebaseNotificationServices.class);
        startService(notificationService);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */,null)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        //navigation menu settings
        NavigationView navView = (NavigationView) findViewById(R.id.nav_view);
        navView.setNavigationItemSelectedListener(this);
        header = navView.getHeaderView(0);
        final Menu navMenu = navView.getMenu();
        final TextView nameView = (TextView) header.findViewById(R.id.nameView);
        final TextView emailView = (TextView) header.findViewById(R.id.emailView);
        final TextView userText = (TextView) findViewById(R.id.userText);

        //Line chart settings
        for (int c : ColorTemplate.VORDIPLOM_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.JOYFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.COLORFUL_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.LIBERTY_COLORS)
            colors.add(c);
        for (int c : ColorTemplate.PASTEL_COLORS)
            colors.add(c);
        colors.add(ColorTemplate.getHoloBlue());

        lineChart = (LineChart) findViewById(R.id.linechart);
        YAxis yRightAxis = lineChart.getAxisRight();
        yRightAxis.setEnabled(false);
        YAxis yLeftAxis = lineChart.getAxisLeft();
        yLeftAxis.setDrawTopYLabelEntry(true);
        yLeftAxis.setDrawGridLines(false);
        yLeftAxis.setAxisMinimum(0f);
        Description dscr = new Description();
        dscr.setText("");
        lineChart.setDrawBorders(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setDescription(dscr);

        //Getting user data and groups
        groups = new ArrayList<>();
        database = FirebaseDatabase.getInstance();
        mAuth = FirebaseAuth.getInstance();
        mAuth.addAuthStateListener(new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                currentUser = mAuth.getCurrentUser();
                if(currentUser != null) {
                    getUserExpenses();
                    name = currentUser.getDisplayName();
                    nameView.setText(name);
                    email = currentUser.getEmail();
                    emailView.setText(email);
                    uid = currentUser.getUid();
                    photoUrl = currentUser.getPhotoUrl().toString();
                    if(photoUrl != null) {
                        PendingIntent pendingResult = createPendingResult(
                                RSS_DOWNLOAD_REQUEST_CODE, new Intent(), 0);
                        Intent intent = new Intent(getApplicationContext(), DownloadIntentService.class);
                        intent.putExtra(DownloadIntentService.URL_EXTRA, photoUrl);
                        intent.putExtra(DownloadIntentService.PENDING_RESULT_EXTRA, pendingResult);
                        startService(intent);
                    }
                    userText.setText("Hey "+name.split(" ")[0]+"! Here you can find your total expenses among all groups!");
                    DatabaseReference groupsRef = database.getReference("users/" + uid + "/groups");
                    groupsRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            groups.clear();
                            navMenu.removeGroup(R.id.groups_menu);
                            for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                                String group = singleSnapshot.getKey();
                                groups.add(group);
                                navMenu.add(R.id.groups_menu,groups.indexOf(group),Menu.NONE,group).setIcon(R.drawable.ic_group_black_24dp);
                            }

                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                }
            }
        });

        Switch cat_switch = (Switch) findViewById(R.id.categories_switch);
        cat_switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    updateChart(expenses, true);
                } else {
                    updateChart(expenses, false);
                }
            }
        });
    }

    private void getUserExpenses(){

        DatabaseReference groupsRef = database.getReference("groups");
        groupsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                expenses.clear();
                categories.clear();
                for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                    if(singleSnapshot.child("members").hasChild(currentUser.getDisplayName())){
                        for(DataSnapshot expense : singleSnapshot.child("expenses").getChildren()){
                            String userAmount = expense.child("division").child(currentUser.getDisplayName()).getValue().toString();
                            String date = (String) expense.child("date").getValue();
                            String category = (String) expense.child("category").getValue();
                            Expense e = new Expense();
                            e.setId(expense.getKey());
                            e.setAmount(Float.parseFloat(userAmount));
                            e.setDate(date);
                            e.setCategory(category);
                            expenses.add(e);
                            if(!categories.contains(category)){
                                categories.add(category);
                            }
                        }
                    }
                }
                Collections.sort(expenses, new Comparator<Expense>() {
                    @Override
                    public int compare(Expense e1, Expense e2){
                        SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy", Locale.ITALY);
                        Date d1 = null;
                        Date d2 = null;
                        try {
                            d1 = format.parse(e1.getDate());
                            d2 = format.parse(e2.getDate());
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return d1.compareTo(d2);
                    }
                });

                updateChart(expenses, false);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void updateChart(ArrayList<Expense> expenses, boolean byCategories){
        lineChart.clear();

        if(expenses.size() != 0){
            if(!byCategories) {
                final ArrayList <Entry> entries = new ArrayList<>();
                final HashMap<Integer, String> dates = new HashMap<>();
                ArrayList<String> addedExp = new ArrayList<>();
                int counter = 0;
                for(int j = 0; j < expenses.size(); j++) {
                    if(!addedExp.contains(expenses.get(j).getId())) {
                        float e_val = expenses.get(j).getAmount();
                        for (int k = j + 1; k < expenses.size(); k++) {
                            if (expenses.get(j).getDate().equals(expenses.get(k).getDate())) {
                                e_val += expenses.get(k).getAmount();
                                addedExp.add(expenses.get(k).getId());
                            }
                        }
                        Entry e = new Entry(counter, e_val, expenses.get(j).getDate());
                        entries.add(e);
                        dates.put(counter,expenses.get(j).getDate());
                        counter++;
                    }
                }
                LineDataSet dataSet = new LineDataSet(entries, "Total");
                dataSet.setDrawValues(false);
                dataSet.setLineWidth(2f);
                dataSet.setDrawCircleHole(false);
                dataSet.setDrawFilled(true);
                dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
                dataSet.setCubicIntensity(.8f);
                XAxis xAxis = lineChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setDrawGridLines(false);
                xAxis.setDrawLabels(true);
                xAxis.setLabelCount(6, true);
                xAxis.setValueFormatter(new IAxisValueFormatter() {
                    @Override
                    public String getFormattedValue(float value, AxisBase axis) {
                        if(dates.get((int)value) != null){
                            return dates.get((int)value)
                                    .replaceAll("-[0-9][0-9][0-9][0-9]", "")
                                    .replace("-", " ");
                        }
                        return "";
                    }
                });
                LineData data = new LineData(dataSet);
                lineChart.setData(data);
                lineChart.invalidate();
            } else {
                final Map <String, ILineDataSet> dataSets = new HashMap<>();
                final Map <Integer,String> dates = new HashMap<>();
                final Map <String, Integer> datesIDs = new HashMap<>();
                for(int k = 0; k < expenses.size(); k++){
                    datesIDs.put(expenses.get(k).getDate(),k);
                    dates.put(k,expenses.get(k).getDate());
                }
                for(int i = 0; i < categories.size(); i++){
                    ArrayList <Entry> entries = new ArrayList<>();
                    for(int j = 0; j < expenses.size(); j++){
                        if(expenses.get(j).getCategory().equals(categories.get(i))){
                            entries.add(new Entry(datesIDs.get(expenses.get(j).getDate()),
                                    expenses.get(j).getAmount(), expenses.get(j).getDate()));
                        }
                    }
                    LineDataSet dataSet = new LineDataSet(entries, categories.get(i));
                    dataSet.setDrawValues(false);
                    dataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
                    dataSet.setCubicIntensity(.8f);
                    dataSet.setDrawCircleHole(false);
                    dataSet.setColor(colors.get(i));
                    dataSet.setCircleColor(colors.get(i));
                    dataSets.put(categories.get(i), dataSet);
                }
                XAxis xAxis = lineChart.getXAxis();
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setDrawGridLines(false);
                xAxis.setDrawLabels(true);
                xAxis.setLabelCount(6, true);
                xAxis.setValueFormatter(new IAxisValueFormatter() {
                    @Override
                    public String getFormattedValue(float value, AxisBase axis) {
                        if (dates.get((int)value) != null){
                            return dates.get((int) value)
                                    .replaceAll("-[0-9][0-9][0-9][0-9]", "")
                                    .replace("-", " ");
                        }
                        return "";
                    }
                });
                LineData data = new LineData(new ArrayList<ILineDataSet>(dataSets.values()));
                lineChart.setData(data);
                lineChart.invalidate();
            }
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.user_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.user, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_share) {

        } else if (id == R.id.nav_logout) {

            Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {
                    FirebaseAuth.getInstance().signOut();
                    Toast.makeText(UserActivity.this,"Succesfully logged out",Toast.LENGTH_LONG).show();
                    Intent loginIntent = new Intent(UserActivity.this,LoginActivity.class);
                    startActivity(loginIntent);
                    finish();
                }
            });

        } else if (id == R.id.add_group){

            Intent i = new Intent(UserActivity.this, AddGroupActivity.class);
            startActivity(i);

        } else {

            Intent i = new Intent(UserActivity.this, GroupActivity.class);
            i.putExtra("selectedGroup",groups.get(id));
            startActivity(i);

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.user_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RSS_DOWNLOAD_REQUEST_CODE) {
            switch (resultCode) {
                case DownloadIntentService.INVALID_URL_CODE:
                    Log.e("Error","Invalid URL");
                    break;
                case DownloadIntentService.ERROR_CODE:
                    Log.e("Error","Error downloading data");
                    break;
                case DownloadIntentService.RESULT_CODE:
                    ImageView userImage = (ImageView) header.findViewById(R.id.userImageView);
                    Bitmap bm = data.getParcelableExtra("url");
                    Bitmap circleBitmap = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);

                    BitmapShader shader = new BitmapShader (bm,  Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Paint paint = new Paint();
                    paint.setShader(shader);
                    paint.setAntiAlias(true);
                    Canvas c = new Canvas(circleBitmap);
                    c.drawCircle(bm.getWidth()/2, bm.getHeight()/2, bm.getWidth()/2, paint);
                    userImage.setImageBitmap(circleBitmap);
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
