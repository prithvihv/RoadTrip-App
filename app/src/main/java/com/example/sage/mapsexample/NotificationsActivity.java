package com.example.sage.mapsexample;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NotificationsActivity extends AppCompatActivity {
    private static final String TAG = "NotificationsActivity";

    // Firebase objects
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore firestoreDB;

    // View objects
    private ListView notificationsListView;

    // Member variables
    private ArrayList<NotificationsListDataModel> notificationsList;
    private NotificationsListAdapter notificationsListAdapter;
    private RequestQueue requestQueue;

    //----------------------------------------------------------------------------------------------
    //      ACTIVITY LIFECYCLE METHODS
    //----------------------------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_notifications);
        requestQueue = Volley.newRequestQueue(this);

        notificationsListView = findViewById(R.id.notifications_list);
        notificationsList = new ArrayList<>();
        notificationsListAdapter = new NotificationsListAdapter(getApplicationContext(), R.layout.notification_list_item, notificationsList);
        notificationsListView.setAdapter(notificationsListAdapter);
        
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        firestoreDB = FirebaseFirestore.getInstance();
        dbGetNotifications();
    }

    //----------------------------------------------------------------------------------------------
    //      MEMBER METHODS
    //----------------------------------------------------------------------------------------------

    void dbGetNotifications() {

        firestoreDB.collection("USERS").document(currentUser.getUid())
                .collection("NOTIFICATIONS")
                .whereEqualTo("Type", "GROUP_INVITE")
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(TAG, document.getId() + " => " + document.getData());
                                final String notificationId = document.getId();
                                final String groupId = document.getString("Group_id");
                                final String senderId = document.getString("Sender_id");

                                firestoreDB.collection("GROUPS").document(groupId)
                                        .get()
                                        .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    DocumentSnapshot document = task.getResult();
                                                    final String groupName = document.getString("groupName");
                                                    String groupDisplayPictureURL = null;
                                                    if (document.contains("ImageURL")) {
                                                        groupDisplayPictureURL = document.getString("ImageURL");
                                                    }
                                                    final String finalGroupDisplayPictureURL = groupDisplayPictureURL;
                                                    firestoreDB.collection("USERS").document(senderId)
                                                            .get()
                                                            .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                                                @Override
                                                                public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                                                    if (task.isSuccessful()) {
                                                                        DocumentSnapshot document = task.getResult();
                                                                        String senderName = document.getString("Name");
                                                                        notificationsList.add(
                                                                                new NotificationsListDataModel(
                                                                                        notificationId,
                                                                                        groupId,
                                                                                        groupName,
                                                                                        senderId,
                                                                                        senderName,
                                                                                        finalGroupDisplayPictureURL
                                                                                )
                                                                        );
                                                                        notificationsListAdapter.notifyDataSetChanged();

                                                                    } else {
                                                                        Log.d(TAG, "get failed with ", task.getException());
                                                                    }
                                                                }
                                                            });

                                                } else {
                                                    Log.d(TAG, "get failed with ", task.getException());
                                                }
                                            }
                                        });

                            }
                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });

    }

    void dbDeleteNotification(String notificationId) {

        firestoreDB.collection("USERS").document(currentUser.getUid())
                .collection("NOTIFICATIONS")
                .document(notificationId)
                .delete()
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully deleted!");
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Error deleting document", e);
            }
        });
    }

    //TODO - figure out way to shift outside front end if possible OR use transactions / batch writing
    public void dbCreateGroupMember(final String groupId, final String userId) {

        // Add groupid to Groups array inside user
        firestoreDB.collection("USERS").document(userId)
                .get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        ArrayList<String> groupIds;
                        if (documentSnapshot.contains("Groups"))
                            groupIds = (ArrayList<String>) documentSnapshot.get("Groups");
                        else
                            groupIds = new ArrayList<>();

                        groupIds.add(groupId);

                        firestoreDB.collection("USERS").document(userId)
                                .update("Groups", groupIds)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {

                                        // Add userid to users array in group
                                        firestoreDB.collection("GROUPS").document(groupId)
                                                .get()
                                                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                                                    @Override
                                                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                                                        ArrayList<String> userIds;
                                                        if (documentSnapshot.contains("Users"))
                                                            userIds = (ArrayList<String>) documentSnapshot.get("Users");
                                                        else
                                                            userIds = new ArrayList<>();

                                                        userIds.add(userId);

                                                        firestoreDB.collection("GROUPS").document(groupId)
                                                                .update("Users", userIds)
                                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                                    @Override
                                                                    public void onSuccess(Void aVoid) {

                                                                        Log.d(TAG, "onSuccess: " + "created group member");
                                                                        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                                                                        startActivity(intent);

                                                                    }
                                                                }).addOnFailureListener(new OnFailureListener() {
                                                            @Override
                                                            public void onFailure(@NonNull Exception e) {
                                                                Log.d(TAG, "onFailure: " + e);
                                                            }
                                                        });
                                                    }
                                                }).addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {

                                            }
                                        });

                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d(TAG, "onFailure: " + e);
                            }
                        });
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {

            }
        });
    }

    //----------------------------------------------------------------------------------------------
    //      NOTIFICATIONS LIST VIEW OBJECTS AND METHODS
    //----------------------------------------------------------------------------------------------

    class NotificationsListAdapter extends ArrayAdapter<NotificationsListDataModel> {

        public NotificationsListAdapter(Context context, int resource, ArrayList<NotificationsListDataModel> items) {
            super(context, resource, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View view = convertView;
            Log.d(TAG, "accessed getView" + "position : " + position);
            if (view == null) {
                LayoutInflater vi;
                vi = LayoutInflater.from(getContext());
                view = vi.inflate(R.layout.notification_list_item, null);
            }

            final NotificationsListDataModel notificationsListDataModelItem = getItem(position);

            if (notificationsListDataModelItem != null) {

                TextView groupNameTV = view.findViewById(R.id.group_name);
                TextView senderNameTV = view.findViewById(R.id.sender_name);
                NetworkImageView groupImageNIV = view.findViewById(R.id.group_image);
                Button acceptBTN = view.findViewById(R.id.accept);
                Button rejectBTN = view.findViewById(R.id.reject);

                groupNameTV.setText(notificationsListDataModelItem.getGroupName());
                senderNameTV.setText(notificationsListDataModelItem.getSenderName());

                ImageLoader imageLoader;
                imageLoader = new ImageLoader(requestQueue, new ImageLoader.ImageCache() {
                    private final LruCache<String, Bitmap> mCache = new LruCache<>(10);

                    public void putBitmap(String url, Bitmap bitmap) {
                        mCache.put(url, bitmap);
                    }

                    public Bitmap getBitmap(String url) {
                        return mCache.get(url);
                    }
                });
                groupImageNIV.setImageUrl(notificationsListDataModelItem.getGroupDisplayPictureURL(), imageLoader);

                acceptBTN.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dbCreateGroupMember(notificationsListDataModelItem.getGroupId(), mAuth.getCurrentUser().getUid());
                        dbDeleteNotification(notificationsListDataModelItem.getNotificationId());

                    }
                });

                rejectBTN.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dbDeleteNotification(notificationsListDataModelItem.getNotificationId());
                        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                        startActivity(intent);
                    }
                });
            }
            return view;
        }
    }

    public class NotificationsListDataModel {
        String notificationId;
        String groupId;
        String groupName;
        String senderId;
        String senderName;
        String groupDisplayPictureURL;

        public NotificationsListDataModel(String notificationId, String groupId, String groupName, String senderId, String senderName, String groupDisplayPictureURL) {
            this.notificationId = notificationId;
            this.groupId = groupId;
            this.groupName = groupName;
            this.senderId = senderId;
            this.senderName = senderName;
            this.groupDisplayPictureURL = groupDisplayPictureURL;
        }

        public String getNotificationId() {
            return notificationId;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public String getSenderId() {
            return senderId;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getGroupDisplayPictureURL() {
            return groupDisplayPictureURL;
        }
    }
}
