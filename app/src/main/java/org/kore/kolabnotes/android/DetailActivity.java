package org.kore.kolabnotes.android;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.kore.kolab.notes.AuditInformation;
import org.kore.kolab.notes.Colors;
import org.kore.kolab.notes.Identification;
import org.kore.kolab.notes.Note;
import org.kore.kolab.notes.Notebook;
import org.kore.kolab.notes.Tag;
import org.kore.kolabnotes.android.content.ActiveAccount;
import org.kore.kolabnotes.android.content.ActiveAccountRepository;
import org.kore.kolabnotes.android.content.NoteRepository;
import org.kore.kolabnotes.android.content.NoteTagRepository;
import org.kore.kolabnotes.android.content.NotebookRepository;
import org.kore.kolabnotes.android.content.TagRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import yuku.ambilwarna.AmbilWarnaDialog;

public class DetailActivity extends ActionBarActivity implements ShareActionProvider.OnShareTargetSelectedListener{

    private final static String HTMLSTART = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0//EN\" \"http://www.w3.org/TR/REC-html40/strict.dtd\">" +
            "<html><head><meta name=\"kolabnotes-richtext\" content=\"1\" /><meta http-equiv=\"Content-Type\" /></head><body>";

    private final static String HTMLEND = "</body></html>";

    private NotebookRepository notebookRepository = new NotebookRepository(this);
    private NoteRepository noteRepository = new NoteRepository(this);
    private NoteTagRepository noteTagRepository = new NoteTagRepository(this);
    private TagRepository tagRepository = new TagRepository(this);
    private ActiveAccountRepository activeAccountRepository = new ActiveAccountRepository(this);

    private Toolbar toolbar;

    private Note note = null;

    private ShareActionProvider shareActionProvider;

    private Intent shareIntent;

    private Note.Classification selectedClassification;

    private org.kore.kolab.notes.Color selectedColor;

    private Set<String> selectedTags = new LinkedHashSet<>();

    private List<String> allTags = new ArrayList<>();

    private boolean notebookSelectionOK = true;

    //Given notebook is set, if a notebook uid was in the start intent,
    //intialNotebook ist the notebook-UID which is selected after setSpinnerSelection was called
    private String givenNotebook;
    private String intialNotebookName;
    private boolean isNewNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");

        // Handle Back Navigation :D
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DetailActivity.this.onBackPressed();
            }
        });

        allTags.addAll(tagRepository.getAll());

        Intent startIntent = getIntent();
        String uid = startIntent.getStringExtra(Utils.NOTE_UID);
        String notebook = startIntent.getStringExtra(Utils.NOTEBOOK_UID);
        String accountEmail = startIntent.getStringExtra(Utils.INTENT_ACCOUNT_EMAIL);
        String rootFolder = startIntent.getStringExtra(Utils.INTENT_ACCOUNT_ROOT_FOLDER);

        Log.d("onCreate","accountEmail:"+accountEmail);
        Log.d("onCreate","rootFolder:"+rootFolder);
        Log.d("onCreate","notebook-uid:"+notebook);

        ActiveAccount activeAccount;
        if(accountEmail != null && rootFolder != null){
            activeAccount = activeAccountRepository.switchAccount(accountEmail,rootFolder);
        }else{
            activeAccount = activeAccountRepository.getActiveAccount();
        }

        initSpinner();

        if(uid != null) {
            note = noteRepository.getByUID(activeAccount.getAccount(), activeAccount.getRootFolder(), uid);

            //Maybe the note got deleted (sync happend after a click on a note was done) => Issues 34 on GitHub
            if (note == null) {
                Toast.makeText(this, R.string.note_not_found, Toast.LENGTH_LONG);
            } else {
                EditText summary = (EditText) findViewById(R.id.detail_summary);
                EditText description = (EditText) findViewById(R.id.detail_description);
                summary.setText(note.getSummary());

                Spanned fromHtml = Html.fromHtml(note.getDescription());

                description.setText(fromHtml, TextView.BufferType.SPANNABLE);

                selectedClassification = note.getClassification();
                for (Tag tag : note.getCategories()) {
                    selectedTags.add(tag.getName());
                }

                selectedColor = note.getColor();
                if (selectedColor != null) {
                    toolbar.setBackgroundColor(Color.parseColor(selectedColor.getHexcode()));
                }
            }
        }else{
            isNewNote = true;
        }

        setNotebook(activeAccount, notebook);
        intialNotebookName = getNotebookSpinnerSelectionName();
    }

    void setNotebook(ActiveAccount activeAccount,String uid){
        if(uid != null) {
            String notebookSummary = notebookRepository.getByUID(activeAccount.getAccount(), activeAccount.getRootFolder(), uid).getSummary();
            setSpinnerSelection(notebookSummary);
            givenNotebook = notebookSummary;
        }
    }

    String getNotebookSpinnerSelectionName(){
        Spinner spinner = (Spinner) findViewById(R.id.spinner_notebook);

        if(spinner.getSelectedItem() == null){
            return null;
        }

        return spinner.getSelectedItem().toString();
    }

    void setSpinnerSelection(String notebookSummary){
        Spinner spinner = (Spinner) findViewById(R.id.spinner_notebook);
        SpinnerAdapter adapter = spinner.getAdapter();
        for(int i=0;i<adapter.getCount();i++){
            String nbsummary = adapter.getItem(i).toString();
            if(nbsummary.equals(notebookSummary)){
                spinner.setSelection(i);
                break;
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.detail_toolbar, menu);

        MenuItem item = menu.findItem(R.id.share);

        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        shareActionProvider.setShareIntent(shareIntent);
        shareActionProvider.setOnShareTargetSelectedListener(this);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.ok_menu:
                saveNote();
                break;
            case R.id.delete_menu:
                deleteNote();
                break;
            case R.id.edit_tag_menu:
                editTags();
                break;
            case R.id.change_classification:
                editClassification();
                break;
            case R.id.colorpicker:
                chooseColor();
                break;
        }
        return true;
    }

    void chooseColor(){

        final int initialColor = selectedColor == null ? Color.WHITE : Color.parseColor(selectedColor.getHexcode());

        AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, initialColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                selectedColor = Colors.getColor(String.format("#%06X", (0xFFFFFF & color)));
                toolbar.setBackgroundColor(color);
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
                // do nothing
            }
        });
        dialog.show();
    }

    void editClassification(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.dialog_change_classification);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_classification, null);

        builder.setView(view);

        builder.setPositiveButton(R.string.ok, new OnClassificationChange(view));
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //nothing
            }
        });

        if(selectedClassification == null){
            ((RadioButton) view.findViewById(R.id.radio_public)).toggle();
        }else {

            switch (selectedClassification) {
                case PUBLIC:
                    ((RadioButton) view.findViewById(R.id.radio_public)).toggle();
                    break;
                case CONFIDENTIAL:
                    ((RadioButton) view.findViewById(R.id.radio_confidential)).toggle();
                    break;
                case PRIVATE:
                    ((RadioButton) view.findViewById(R.id.radio_private)).toggle();
                    break;
            }
        }

        builder.show();
    }

    class OnClassificationChange implements DialogInterface.OnClickListener {

        private final View view;

        public OnClassificationChange(View view){
            this.view = view;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            RadioGroup group = (RadioGroup) view.findViewById(R.id.dialog_classification);
            switch(group.getCheckedRadioButtonId()){
                case R.id.radio_public:
                    DetailActivity.this.selectedClassification = Note.Classification.PUBLIC;
                    break;
                case R.id.radio_confidential:
                    DetailActivity.this.selectedClassification = Note.Classification.CONFIDENTIAL;
                    break;
                case R.id.radio_private:
                    DetailActivity.this.selectedClassification = Note.Classification.PRIVATE;
                    break;
            }
        }
    }

    void editTags(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_change_tags);

        final String[] tagArr = allTags.toArray(new String[allTags.size()]);
        final boolean[] selectionArr = new boolean[tagArr.length];

        final ArrayList<Integer> selectedItems=new ArrayList<Integer> ();

        for(int i=0;i<tagArr.length;i++){
            if(selectedTags.contains(tagArr[i])){
                selectionArr[i] = true;
                selectedItems.add(i);
            }
        }

        builder.setMultiChoiceItems(tagArr, selectionArr,
                new DialogInterface.OnMultiChoiceClickListener() {



                    @Override
                    public void onClick(DialogInterface dialog, int indexSelected,
                                        boolean isChecked) {
                        if (isChecked) {
                            // If the user checked the item, add it to the selected items
                            selectedItems.add(indexSelected);
                        } else if (selectedItems.contains(indexSelected)) {
                            // Else, if the item is already in the array, remove it
                            selectedItems.remove(Integer.valueOf(indexSelected));
                        }
                    }
                })
                // Set the action buttons
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        selectedTags.clear();
                        for (int i = 0; i < selectedItems.size(); i++) {
                            selectedTags.add(tagArr[selectedItems.get(i)]);
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // nothing

                    }
                });

        builder.show();
    }

    String getDescriptionFromView(){
        EditText description =(EditText) findViewById(R.id.detail_description);

        if(description.getText() != null){
            StringBuilder sb = new StringBuilder(HTMLSTART);
            sb.append(Html.toHtml(description.getText()));
            sb.append(HTMLEND);
            return sb.toString();
        }
        return null;
    }

    private AlertDialog createNotebookDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.dialog_input_text_notebook);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_text_input, null);

        builder.setView(view);

        builder.setPositiveButton(R.string.ok,new CreateNotebookButtonListener((EditText)view.findViewById(R.id.dialog_text_input_field)));
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                notebookSelectionOK = false;
            }
        });
        return builder.create();
    }

    public class CreateNotebookButtonListener implements DialogInterface.OnClickListener{

        private final EditText textField;

        public CreateNotebookButtonListener(EditText textField) {
            this.textField = textField;
        }


        @Override
        public void onClick(DialogInterface dialog, int which) {
            if(textField == null || textField.getText() == null || textField.getText().toString().trim().length() == 0){
                notebookSelectionOK = false;
                return;
            }

            ActiveAccount activeAccount = activeAccountRepository.getActiveAccount();

            Identification ident = new Identification(UUID.randomUUID().toString(),"kolabnotes-android");
            Timestamp now = new Timestamp(System.currentTimeMillis());
            AuditInformation audit = new AuditInformation(now,now);

            String value = textField.getText().toString();

            Notebook nb = new Notebook(ident,audit, Note.Classification.PUBLIC, value);
            nb.setDescription(value);
            notebookRepository.insert(activeAccount.getAccount(), activeAccount.getRootFolder(), nb);
            notebookSelectionOK = true;
            setSpinnerSelection(value);
        }
    }

    void saveNote(){
        EditText summary = (EditText) findViewById(R.id.detail_summary);

        Spinner spinner = (Spinner) findViewById(R.id.spinner_notebook);

        if(spinner.getSelectedItem() == null){
            //Just possible if there is no notebook created
            AlertDialog notebookDialog = createNotebookDialog();

            notebookDialog.show();

            if(!notebookSelectionOK){
                return;
            }
        }

        if(TextUtils.isEmpty(summary.getText().toString())){
            summary.setError(getString(R.string.error_field_required));
            summary.requestFocus();
            return;
        }

        String notebookName = getNotebookSpinnerSelectionName();

        String descriptionValue = getDescriptionFromView();


        if(note == null){
            final String uuid = UUID.randomUUID().toString();
            Identification ident = new Identification(uuid,"kolabnotes-android");
            Timestamp now = new Timestamp(System.currentTimeMillis());
            AuditInformation audit = new AuditInformation(now,now);

            note = new Note(ident,audit, selectedClassification == null ? Note.Classification.PUBLIC : selectedClassification, summary.getText().toString());
            note.setDescription(descriptionValue);
            note.setColor(selectedColor);

            Notebook book =  notebookRepository.getBySummary(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),notebookName);

            noteRepository.insert(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),note,book.getIdentification().getUid());
            noteTagRepository.delete(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),uuid);
            for(String tag : selectedTags){
                noteTagRepository.insert(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),uuid,tag);
            }
        }else{
            final String uuid = note.getIdentification().getUid();
            note.setSummary(summary.getText().toString());
            note.setDescription(descriptionValue);
            note.setClassification(selectedClassification);
            note.setColor(selectedColor);
            note.getAuditInformation().setLastModificationDate(System.currentTimeMillis());

            Notebook book =  notebookRepository.getBySummary(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),notebookName);

            noteRepository.update(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),note,book.getIdentification().getUid());

            noteTagRepository.delete(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),uuid);
            for(String tag : selectedTags){
                noteTagRepository.insert(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),uuid,tag);
            }
        }

        Intent returnIntent = new Intent();
        if(isNewNote || givenNotebook != null) {
            returnIntent.putExtra("selectedNotebookName", notebookName);
        }
        Utils.updateWidgetsForChange(getApplication());

        setResult(RESULT_OK,returnIntent);
        finish();
    }

    void deleteNote(){
        if(note != null){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setTitle(R.string.dialog_delete_note);
            builder.setMessage(R.string.dialog_question_delete);
            builder.setPositiveButton(R.string.yes,new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    DetailActivity.this.noteRepository.delete(  activeAccountRepository.getActiveAccount().getAccount(), activeAccountRepository.getActiveAccount().getRootFolder(),note);

                    Utils.updateWidgetsForChange(getApplication());

                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("selectedNotebookName",givenNotebook);
                    setResult(RESULT_OK,returnIntent);
                    finish();
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //nothing
                }
            });
            builder.show();
        }
    }

    @Override
    public boolean onShareTargetSelected(ShareActionProvider shareActionProvider, Intent intent) {
        if(note == null || note.getDescription() == null) {
            shareIntent.putExtra(Intent.EXTRA_TEXT, "");
        }else{
            shareIntent.putExtra(Intent.EXTRA_TEXT, note.getDescription());
        }
        return false;
    }

    void initSpinner(){
        Spinner spinner = (Spinner) findViewById(R.id.spinner_notebook);

        List<Notebook> notebooks = notebookRepository.getAll(  activeAccountRepository.getActiveAccount().getAccount(),  activeAccountRepository.getActiveAccount().getRootFolder());

        String[] notebookArr = new String[notebooks.size()];

        for(int i=0; i<notebooks.size();i++){
            notebookArr[i] = notebooks.get(i).getSummary();
        }

        Arrays.sort(notebookArr);

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(this,R.layout.notebook_spinner_item,notebookArr);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //outState.putParcelable("appInfo", appInfo.getComponentName());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if(note != null){

            EditText summary = (EditText) findViewById(R.id.detail_summary);

            Spinner spinner = (Spinner) findViewById(R.id.spinner_notebook);


            Note newNote = new Note(note.getIdentification(),note.getAuditInformation(),selectedClassification == null ? Note.Classification.PUBLIC : selectedClassification, summary.getText().toString());
            newNote.setDescription(getDescriptionFromView());
            newNote.setColor(selectedColor);

            Tag[] tags = new Tag[selectedTags.size()];
            int i=0;
            for(String tag : selectedTags){
                tags[i++] = new Tag(tag);
            }

            newNote.addCategories(tags);

            String nb = spinner.getSelectedItem().toString();

            boolean nbSameNames = Objects.equals(intialNotebookName,nb);
            boolean differences = Utils.differentMutableData(note,newNote) || !nbSameNames;

            if(differences) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setTitle(R.string.dialog_cancel_warning);
                builder.setMessage(R.string.dialog_question_cancel);
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        goBack();
                    }
                });
                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //nothing
                    }
                });
                builder.show();
            }else{
                goBack();
            }
        }else{
            goBack();
        }
    }

    void goBack(){
        Intent returnIntent = new Intent();
        returnIntent.putExtra("selectedNotebookName",givenNotebook);
        setResult(RESULT_CANCELED,returnIntent);

        finish();
    }
}
