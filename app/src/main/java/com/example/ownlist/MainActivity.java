/*
 * Copyright Christoph Gritschenberger 2014.
 *
 * This file is part of OwnList.
 *
 * OwnList is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OwnList is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OwnList.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.example.ownlist;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.example.listsync.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);

    private RepositoryBackedAdapter adapter;
    private UpdatingListSyncer listSyncer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupInputField();

        final ListView listview = (ListView) findViewById(R.id.items_list);
        File syncDir = new File(getFilesDir(), "shopping@owncloud");

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        String host = sharedPrefs.getString("server_host_address", "");
        String directoryPath = sharedPrefs.getString("server_remote_path", "");
        int port = Integer.parseInt(sharedPrefs.getString("server_port", "8080"));
        String username = sharedPrefs.getString("server_user", "");
        String password = sharedPrefs.getString("server_password", "");
        WebDavConfiguration config = WebDavConfiguration.builder(host, directoryPath)
                .usingSSL()
                .customPort(port)
                .credentials(username, password)
                .build();
        WebDavRepository repository = new WebDavRepository(config);

        listSyncer = new UpdatingListSyncer(new ListRepository("test", repository));
        listSyncer.registerExceptionHandler(new Consumer<Exception>() {
            @Override
            public void consume(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        adapter = new RepositoryBackedAdapter(listSyncer);
        listview.setAdapter(adapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view,
                                    int position, long id) {
                LOGGER.info("clicked listitem @{}", position);
                adapter.toggle(position);
            }

        });
    }

    private void setupInputField() {
        AutoCompleteTextView viewById = (AutoCompleteTextView) findViewById(R.id.input_item);
        viewById.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{
                "Brot", "Milch"
        }));
        viewById.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    addItemToList(v);
                }
                return true;
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void addItemToList(View view) {
        Editable text = ((AutoCompleteTextView) findViewById(R.id.input_item)).getText();
        if (text == null) {
            return;
        }
        adapter.addItem(text.toString());
        text.clear();
    }

    @Override
    protected void onStart() {
        LOGGER.info("starting");
        listSyncer.setUpdateTimeout(10, TimeUnit.SECONDS);
        super.onStart();
    }

    @Override
    protected void onStop() {
        LOGGER.info("stopping");
        listSyncer.deactivate();
        super.onStop();
    }

    private class RepositoryBackedAdapter extends BaseAdapter {
        private ListSyncer source;

        private RepositoryBackedAdapter(ListSyncer source) {
            this.source = source;
            source.registerChangeListener(new Consumer<List<CheckItem>>() {
                @Override
                public void consume(List<CheckItem> value) {
                    doNotifyDataSetChanged();
                }
            });
        }

        private void doNotifyDataSetChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }

        @Override
        public int getCount() {
            return source.getLocal().size();
        }

        @Override
        public Object getItem(int position) {
            return source.getLocal().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public void addItem(final String text) {
            new Thread() {
                @Override
                public void run() {
                    source.add(new CheckItem(text));
                    doNotifyDataSetChanged();
                }
            }.start();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = LayoutInflater.from(MainActivity.this);
                v = vi.inflate(R.layout.list_item_layout, null);
            }
            if (v == null) {
                throw new NullPointerException("view was null");
            }
            CheckItem p = source.getLocal().get(position);
            CheckBox checkBox = (CheckBox) v.findViewById(R.id.checkBox);
            checkBox.setText(p.getText());
            checkBox.setChecked(p.isChecked());
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle(position);
                }
            });
            Button removeButton = (Button) v.findViewById(R.id.remove);
            removeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final CheckItem remove = source.getLocal().get(position);
                    new Thread() {
                        @Override
                        public void run() {
                            source.remove(remove);
                            doNotifyDataSetChanged();
                        }
                    }.start();
                }
            });
            return v;
        }

        private void toggle(int position) {
            final CheckItem checkItem = source.getLocal().get(position);
            new Thread() {
                @Override
                public void run() {
                    source.remove(checkItem);
                    CheckItem changed = checkItem.toggleChecked();
                    source.add(changed);
                    doNotifyDataSetChanged();
                }
            }.start();

        }

    }
}
