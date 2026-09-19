package top.dingfengbo.mylibrary.ui.books

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.ui.common.RefPickerDialog
import top.dingfengbo.mylibrary.ui.common.errorMessage

enum class FormSection(val titleRes: Int) {
    Publishing(R.string.form_section_publishing),
    Purchase(R.string.form_section_purchase),
    Shelving(R.string.form_section_shelving),
    Flags(R.string.form_section_flags),
    Content(R.string.form_section_content),
}

/**
 * The 35-field book form, sectioned.
 *
 * ISBN + title + authors stay pinned at the top because "scan, fix the title, save" is the common
 * path; everything else is a correction you make occasionally, so it lives in collapsible sections
 * instead of ten screens of scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFormScreen(
    container: AppContainer,
    bookId: Int?,
    onOpenScanner: () -> Unit,
    onFinished: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookFormViewModel = viewModel {
        BookFormViewModel(
            books = container.bookRepository,
            catalog = container.catalogRepository,
            preferences = container.preferencesRepository,
            libraryEvents = container.libraryEvents,
            scanHandoff = container.scanHandoff,
            bookId = bookId,
        )
    },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var expanded by remember {
        mutableStateOf(if (bookId == null) setOf(FormSection.Publishing) else FormSection.entries.toSet())
    }

    LaunchedEffect(ui.saved) {
        if (ui.saved) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (ui.isEdit) R.string.form_title_edit else R.string.form_title_create
                        )
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = {
                    TextButton(onClick = viewModel::submit, enabled = ui.canSubmit) {
                        Text(stringResource(R.string.form_save))
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        val loadError = ui.loadError
        if (loadError != null) {
            // A blank form with no message above it is how a failed detail load let the user type a
            // couple of fields and save them over the record, erasing everything the load held back.
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(errorMessage(loadError) ?: stringResource(R.string.error_unknown))
                Spacer(Modifier.height(12.dp))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.error_retry)) }
            }
            return@Scaffold
        }

        if (ui.loading || (ui.isEdit && !ui.recordLoaded)) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val form = ui.form
        LazyColumn(
            // The activity is edge-to-edge, so the window is not resized by the keyboard on API 30+:
            // without this the lower fields (and the save button) sit under the IME.
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = form.isbn,
                        onValueChange = { value -> viewModel.edit { it.copy(isbn = value) } },
                        label = { Text(stringResource(R.string.book_detail_field_isbn)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    // Scanning identifies a book, so it belongs to the create flow only: the form an
                    // edit writes back is the record, and a scan there used to replace it.
                    if (!ui.isEdit) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onOpenScanner) { Text(stringResource(R.string.form_scan)) }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::lookupIsbn,
                        enabled = form.isbn.isNotBlank() && ui.lookup != LookupOutcome.Running,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (ui.lookup == LookupOutcome.Running) {
                            CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.form_lookup))
                        }
                    }
                }
                LookupStatus(ui.lookup)
            }

            item {
                OutlinedTextField(
                    value = form.titleCn,
                    onValueChange = { value -> viewModel.edit { it.copy(titleCn = value) } },
                    label = { Text(stringResource(R.string.form_title_cn)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { value -> viewModel.edit { it.copy(title = value) } },
                    label = { Text(stringResource(R.string.form_title_original)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextFieldRow(R.string.book_detail_field_translator, form.translator) { value ->
                    viewModel.edit { it.copy(translator = value) }
                }
            }
            item {
                PickerRow(
                    labelRes = R.string.form_authors,
                    chosen = form.authors,
                    onClick = { viewModel.openPicker(RefKind.Author) },
                )
            }

            section(FormSection.Publishing, open = FormSection.Publishing in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Publishing, open)
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerRow(R.string.book_detail_field_publisher, listOfNotNull(form.publisher)) {
                        viewModel.openPicker(RefKind.Publisher)
                    }
                    TextFieldRow(R.string.book_detail_field_publish_date, form.publishDate) { value ->
                        viewModel.edit { it.copy(publishDate = value) }
                    }
                    PickerRow(R.string.book_detail_field_brand, listOfNotNull(form.brand)) {
                        viewModel.openPicker(RefKind.Brand)
                    }
                    PickerRow(R.string.book_detail_field_series, listOfNotNull(form.series)) {
                        viewModel.openPicker(RefKind.Series)
                    }
                    TextFieldRow(R.string.book_detail_field_binding, form.bindingType) { value ->
                        viewModel.edit { it.copy(bindingType = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_paper, form.paperType) { value ->
                        viewModel.edit { it.copy(paperType = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_pages, form.pages, KeyboardType.Number) { value ->
                        viewModel.edit { it.copy(pages = value) }
                    }
                    TextFieldRow(R.string.form_book_count, form.bookCount, KeyboardType.Number) { value ->
                        viewModel.edit { it.copy(bookCount = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_language, form.language) { value ->
                        viewModel.edit { it.copy(language = value) }
                    }
                    TextFieldRow(R.string.form_compose, form.composeType) { value ->
                        viewModel.edit { it.copy(composeType = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_edition, form.edition) { value ->
                        viewModel.edit { it.copy(edition = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_printing, form.printingInfo) { value ->
                        viewModel.edit { it.copy(printingInfo = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_printing_count, form.printedNumber, KeyboardType.Number) { value ->
                        viewModel.edit { it.copy(printedNumber = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_price, form.price, KeyboardType.Decimal) { value ->
                        viewModel.edit { it.copy(price = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_douban, form.doubanScore, KeyboardType.Decimal) { value ->
                        viewModel.edit { it.copy(doubanScore = value) }
                    }
                }
            }

            section(FormSection.Purchase, open = FormSection.Purchase in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Purchase, open)
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextFieldRow(R.string.book_detail_field_purchase_price, form.purchasePrice, KeyboardType.Decimal) { value ->
                        viewModel.edit { it.copy(purchasePrice = value) }
                    }
                    TextFieldRow(R.string.book_detail_field_purchase_date, form.purchaseDate) { value ->
                        viewModel.edit { it.copy(purchaseDate = value) }
                    }
                    PickerRow(
                        labelRes = R.string.book_detail_field_purchase_store,
                        chosen = form.purchaseStore.takeIf { it.isNotBlank() }
                            ?.let { listOf(RefChoice(-1, it)) }.orEmpty(),
                        onClick = { viewModel.openPicker(RefKind.PurchaseStore) },
                    )
                }
            }

            section(FormSection.Shelving, open = FormSection.Shelving in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Shelving, open)
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerRow(R.string.book_detail_field_category, listOfNotNull(form.category)) {
                        viewModel.openPicker(RefKind.Category)
                    }
                    PickerRow(R.string.book_detail_field_bookshelf, listOfNotNull(form.bookshelf)) {
                        viewModel.openPicker(RefKind.Bookshelf)
                    }
                    ReadStateRow(
                        current = form.readState,
                        onChange = { value -> viewModel.edit { it.copy(readState = value) } },
                    )
                    TextFieldRow(R.string.form_tags, form.tags, hint = R.string.form_tags_hint) { value ->
                        viewModel.edit { it.copy(tags = value) }
                    }
                }
            }

            section(FormSection.Flags, open = FormSection.Flags in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Flags, open)
            }) {
                Column {
                    SwitchRow(R.string.form_in_wish, form.inWish) { checked ->
                        viewModel.edit { it.copy(inWish = checked) }
                    }
                    SwitchRow(R.string.form_registered, form.registered) { checked ->
                        viewModel.edit { it.copy(registered = checked) }
                    }
                }
            }

            section(FormSection.Content, open = FormSection.Content in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Content, open)
            }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextFieldRow(R.string.form_thumb, form.thumbImage) { value ->
                        viewModel.edit { it.copy(thumbImage = value) }
                    }
                    if (ui.isEdit) {
                        TextFieldRow(R.string.book_detail_field_link, form.link) { value ->
                            viewModel.edit { it.copy(link = value) }
                        }
                    }
                    MultilineRow(R.string.form_summary, form.summary) { value ->
                        viewModel.edit { it.copy(summary = value) }
                    }
                    MultilineRow(R.string.form_introduction, form.introduction) { value ->
                        viewModel.edit { it.copy(introduction = value) }
                    }
                    MultilineRow(R.string.book_detail_field_catalog, form.catalog) { value ->
                        viewModel.edit { it.copy(catalog = value) }
                    }
                }
            }

            item {
                val message = when {
                    ui.submitError != null -> errorMessage(ui.submitError)
                    !form.canSubmit -> stringResource(R.string.form_required_hint)
                    else -> null
                }
                if (message != null) {
                    Text(
                        text = message,
                        color = if (ui.submitError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = viewModel::submit,
                    enabled = ui.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ui.submitting) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.form_save))
                    }
                }
            }
        }
    }

    val picker = ui.openPicker
    if (picker != null) {
        RefPickerDialog(
            titleRes = picker.titleRes,
            selected = viewModel.choicesFor(picker),
            multi = picker == RefKind.Author,
            search = { query -> viewModel.search(picker, query) },
            createFields = when (picker) {
                RefKind.Author -> listOf(R.string.form_author_name, R.string.form_author_name_cn)
                RefKind.PurchaseStore -> emptyList()
                else -> listOf(R.string.form_publisher_name)
            },
            // Purchase stores come from server configuration and the view model refuses to create
            // one, so offering the action would only ever end in a failure.
            onCreate = if (picker == RefKind.PurchaseStore) null else { values ->
                viewModel.create(picker, values)
            },
            onConfirm = { choices -> viewModel.setChoice(picker, choices) },
            onDismiss = { viewModel.openPicker(null) },
        )
    }
}

private val RefKind.titleRes: Int
    get() = when (this) {
        RefKind.Author -> R.string.form_authors
        RefKind.Publisher -> R.string.book_detail_field_publisher
        RefKind.Brand -> R.string.book_detail_field_brand
        RefKind.Series -> R.string.book_detail_field_series
        RefKind.Category -> R.string.book_detail_field_category
        RefKind.Bookshelf -> R.string.book_detail_field_bookshelf
        RefKind.PurchaseStore -> R.string.book_detail_field_purchase_store
    }

private fun toggle(current: Set<FormSection>, section: FormSection, open: Boolean): Set<FormSection> =
    if (open) current + section else current - section

@Composable
private fun LookupStatus(outcome: LookupOutcome?) {
    val text = when (outcome) {
        null, LookupOutcome.Running -> return
        is LookupOutcome.Filled -> outcome.source?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.form_lookup_filled, it)
        } ?: stringResource(R.string.form_lookup_filled_unknown_source)

        LookupOutcome.NothingFound -> stringResource(R.string.form_lookup_nothing)
        is LookupOutcome.Failed -> errorMessage(outcome.throwable) ?: ""
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (outcome is LookupOutcome.Failed) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TextFieldRow(
    labelRes: Int,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    hint: Int? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = hint?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MultilineRow(labelRes: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PickerRow(labelRes: Int, chosen: List<RefChoice>, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = chosen.takeIf { it.isNotEmpty() }?.joinToString("、") { it.label }
                ?: stringResource(R.string.ref_pick),
            modifier = Modifier.weight(1f),
            color = if (chosen.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ReadStateRow(current: String, onChange: (String) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.book_detail_field_read_state),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReadState.entries.forEach { state ->
                FilterChip(
                    selected = current == state.value,
                    onClick = { onChange(state.value) },
                    label = { Text(stringResource(state.labelRes)) },
                )
            }
            // Legacy rows hold values outside the four the web client writes (for example 在读);
            // showing it as a chip keeps an edit from silently rewriting the field.
            if (current.isNotBlank() && ReadState.labelResFor(current) == null) {
                FilterChip(selected = true, onClick = {}, label = { Text(current) })
            }
        }
    }
}

/**
 * One collapsible form section: a header row, and the fields only when it is open.
 */
private fun LazyListScope.section(
    section: FormSection,
    open: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    item { CollapsibleHeader(section, open, onToggle) }
    if (open) item { Column { content() } }
}

@Composable
private fun CollapsibleHeader(section: FormSection, open: Boolean, onToggle: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onToggle(!open) }) {
                Text(
                    stringResource(if (open) R.string.form_collapse else R.string.form_expand)
                )
            }
        }
    }
}
