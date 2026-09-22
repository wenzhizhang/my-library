package top.dingfengbo.mylibrary.ui.books

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import top.dingfengbo.mylibrary.R
import top.dingfengbo.mylibrary.data.AppContainer
import top.dingfengbo.mylibrary.theme.IdentifierTextStyle
import top.dingfengbo.mylibrary.theme.NumericTextStyle
import top.dingfengbo.mylibrary.theme.Spacing
import top.dingfengbo.mylibrary.ui.common.DetailSkeleton
import top.dingfengbo.mylibrary.ui.common.ErrorState
import top.dingfengbo.mylibrary.ui.common.RefPickerDialog
import top.dingfengbo.mylibrary.ui.common.errorMessage

/** Declaration order is the order the sections appear in, top to bottom. */
enum class FormSection(val titleRes: Int) {
    Shelving(R.string.form_section_shelving),
    Publishing(R.string.form_section_publishing),
    Purchase(R.string.form_section_purchase),
    Flags(R.string.form_section_flags),
    Content(R.string.form_section_content),
}

/**
 * The 35-field book form, sectioned.
 *
 * ISBN + title + authors stay pinned at the top because "scan, fix the title, save" is the common
 * path; everything else is a correction you make occasionally, so it lives in collapsible sections
 * instead of ten screens of scrolling. Each section carries a header heavy enough to read as a
 * heading (title weight, accent, its own divider) rather than as one more field label, so the eye
 * can skip to the group it wants — and the save action rides at the bottom of the screen, because a
 * form this long should never demand a scroll back to the top to commit.
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
    var expanded by remember(bookId) {
        // Creating, every optional section starts closed so the common path stays short; editing,
        // the record's fields are the reason the screen is open, so they all start visible.
        mutableStateOf(if (bookId == null) emptySet() else FormSection.entries.toSet())
    }

    // What the form looked like when it arrived: the record under edit, or nothing at all when the
    // book is new. Every later change — typing, a scan, an ISBN lookup filling ten fields — is a
    // difference from this, which is what makes the save bar worth showing.
    var baseline by remember(bookId) { mutableStateOf<BookFormState?>(null) }
    LaunchedEffect(ui.loading, ui.recordLoaded) {
        if (baseline == null && !ui.loading && ui.recordLoaded) baseline = ui.form
    }
    val dirty = baseline != null && baseline != ui.form

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
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::submit, enabled = ui.canSubmit) {
                        Text(stringResource(R.string.form_save))
                    }
                },
            )
        },
        bottomBar = {
            // The bar owns the IME inset. The activity is edge-to-edge and the window is not resized
            // by the keyboard on API 30+, so without this the save button would end up under the IME
            // while the reader is still typing in the field that brought the keyboard up.
            Column(Modifier.imePadding()) {
                AnimatedVisibility(
                    visible = dirty || ui.submitting,
                    enter = slideInVertically(animationSpec = tween(220), initialOffsetY = { it }) +
                        fadeIn(animationSpec = tween(220)),
                    exit = slideOutVertically(animationSpec = tween(220), targetOffsetY = { it }) +
                        fadeOut(animationSpec = tween(220)),
                ) {
                    SaveBar(
                        canSubmit = ui.canSubmit,
                        submitting = ui.submitting,
                        submitError = ui.submitError,
                        requiredHint = !ui.form.canSubmit,
                        onSave = viewModel::submit,
                    )
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        val loadError = ui.loadError
        if (loadError != null) {
            // A blank form with no message above it is how a failed detail load let the user type a
            // couple of fields and save them over the record, erasing everything the load held back.
            ErrorState(
                message = errorMessage(loadError) ?: stringResource(R.string.error_unknown),
                onRetry = viewModel::load,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        if (ui.loading || (ui.isEdit && !ui.recordLoaded)) {
            DetailSkeleton(
                modifier = Modifier.fillMaxSize().padding(padding),
                rows = 6,
            )
            return@Scaffold
        }

        val form = ui.form
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            // Field-to-field gaps: the section breaks are carried by the headers' own top padding,
            // which is why a section reads as further away than the field under it.
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                SectionHeader(
                    R.string.form_section_identity,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }

            if (!ui.isEdit) {
                // What is required, stated before the first field rather than discovered when the
                // save button refuses. Shown for the whole life of a new form: a hint that vanishes
                // mid-typing would shift the field the reader is looking at.
                item { RequiredNote(stringResource(R.string.form_required_hint)) }
            }

            item {
                // Top-aligned: the field carries a supporting line the scan button should not be
                // centred against.
                Row(verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = form.isbn,
                        onValueChange = { value -> viewModel.edit { it.copy(isbn = value) } },
                        label = { Text(stringResource(R.string.book_detail_field_isbn)) },
                        singleLine = true,
                        isError = ui.lookup is LookupOutcome.Failed,
                        // Monospace: an ISBN is data to be read digit by digit, not prose.
                        textStyle = MaterialTheme.typography.bodyLarge.merge(IdentifierTextStyle),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        // The lookup reports itself where the code was typed, so the answer to "did
                        // that work?" is under the field rather than in a banner elsewhere.
                        supportingText = ui.lookup?.let { outcome -> { LookupStatus(outcome) } },
                        trailingIcon = if (ui.lookup == LookupOutcome.Running) {
                            {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else null,
                        modifier = Modifier.weight(1f),
                    )
                    // Scanning identifies a book, so it belongs to the create flow only: the form an
                    // edit writes back is the record, and a scan there used to replace it.
                    if (!ui.isEdit) {
                        Spacer(Modifier.width(Spacing.sm))
                        OutlinedButton(onClick = onOpenScanner) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(stringResource(R.string.form_scan))
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = viewModel::lookupIsbn,
                    enabled = form.isbn.isNotBlank() && ui.lookup != LookupOutcome.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.form_lookup))
                }
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

            section(FormSection.Shelving, open = FormSection.Shelving in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Shelving, open)
            }) {
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

            section(FormSection.Publishing, open = FormSection.Publishing in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Publishing, open)
            }) {
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

            section(FormSection.Purchase, open = FormSection.Purchase in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Purchase, open)
            }) {
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

            section(FormSection.Flags, open = FormSection.Flags in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Flags, open)
            }) {
                SwitchRow(R.string.form_in_wish, form.inWish) { checked ->
                    viewModel.edit { it.copy(inWish = checked) }
                }
                SwitchRow(R.string.form_registered, form.registered) { checked ->
                    viewModel.edit { it.copy(registered = checked) }
                }
            }

            section(FormSection.Content, open = FormSection.Content in expanded, onToggle = { open ->
                expanded = toggle(expanded, FormSection.Content, open)
            }) {
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

/**
 * The bottom save bar: the same action as the top bar's, where the thumb already is, plus the
 * reason it is unavailable when it is.
 */
@Composable
private fun SaveBar(
    canSubmit: Boolean,
    submitting: Boolean,
    submitError: Throwable?,
    requiredHint: Boolean,
    onSave: () -> Unit,
) {
    val error = errorMessage(submitError)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val message = error
                ?: if (requiredHint) stringResource(R.string.form_required_hint) else null
            if (message != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = if (error != null) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Button(
                onClick = onSave,
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(stringResource(R.string.form_save))
            }
        }
    }
}

/**
 * The lookup's four states, in the ISBN field's own supporting line: running, filled (naming where
 * the data came from), nothing found, and failed. Only the failure is coloured as one — "no data for
 * this ISBN" is an ordinary answer, and painting it red teaches the reader to fear the lookup.
 */
@Composable
private fun LookupStatus(outcome: LookupOutcome) {
    val failed = outcome is LookupOutcome.Failed
    val text = when (outcome) {
        LookupOutcome.Running -> stringResource(R.string.form_lookup_running)
        is LookupOutcome.Filled -> outcome.source?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.form_lookup_filled, it)
        } ?: stringResource(R.string.form_lookup_filled_unknown_source)

        LookupOutcome.NothingFound -> stringResource(R.string.form_lookup_nothing)
        is LookupOutcome.Failed -> errorMessage(outcome.throwable)
            ?: stringResource(R.string.error_unknown)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (outcome !is LookupOutcome.Running) {
            Icon(
                imageVector = when {
                    failed -> Icons.Default.Warning
                    outcome == LookupOutcome.NothingFound -> Icons.Default.Info
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = when {
                    failed -> MaterialTheme.colorScheme.error
                    outcome == LookupOutcome.NothingFound -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                failed -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "ISBN and a title are required", stated once, before the fields it is about. */
@Composable
private fun RequiredNote(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextFieldRow(
    labelRes: Int,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    hint: Int? = null,
    onChange: (String) -> Unit,
) {
    val numeric = keyboard == KeyboardType.Number || keyboard == KeyboardType.Decimal
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        placeholder = hint?.let { { Text(stringResource(it)) } },
        singleLine = true,
        // Figures that end up in a column — prices, counts — use tabular digits.
        textStyle = if (numeric) MaterialTheme.typography.bodyLarge.merge(NumericTextStyle)
        else MaterialTheme.typography.bodyLarge,
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

/**
 * A reference picked from a list. The label sits above rather than inside the button: a button is
 * not a text field, and borrowing the outlined field's floating-label trick made the two rows read
 * as different controls.
 */
@Composable
private fun PickerRow(labelRes: Int, chosen: List<RefChoice>, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = chosen.takeIf { it.isNotEmpty() }?.joinToString("、") { it.label }
                    ?: stringResource(R.string.ref_pick),
                modifier = Modifier.weight(1f),
                color = if (chosen.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
 * One collapsible form section: a header that reads as a heading, and the fields only when it is
 * open. The body stays composed through the collapse so the chevron and the fields move together
 * instead of the content appearing already-shrunk.
 */
private fun LazyListScope.section(
    section: FormSection,
    open: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    item {
        SectionHeader(
            titleRes = section.titleRes,
            modifier = Modifier.padding(top = Spacing.xxl),
            open = open,
            onToggle = onToggle,
        )
    }
    item {
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(animationSpec = tween(220)),
            exit = shrinkVertically(animationSpec = tween(220)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) { content() }
        }
    }
}

/**
 * A section header: divider, accent title, chevron. The whole row toggles — the chevron is the
 * affordance, not a 24dp target — and the label is spoken with the action ("collapse") rather than
 * leaving the chevron to describe itself.
 */
@Composable
private fun SectionHeader(
    titleRes: Int,
    modifier: Modifier = Modifier,
    open: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
) {
    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = tween(200),
        label = "section-chevron",
    )
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    enabled = onToggle != null,
                    onClickLabel = stringResource(
                        if (open) R.string.form_collapse else R.string.form_expand
                    ),
                ) { onToggle?.invoke(!open) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onToggle != null) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    // Decorative: the row it sits in is the control, and it already carries the
                    // expand/collapse action label.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp).rotate(chevron),
                )
            }
        }
    }
}
