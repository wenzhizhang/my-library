// Basic JavaScript for My Library

function filterSelect(query, selectId) {
    const select = document.getElementById(selectId);
    const options = select.options;
    const lowerQuery = query.toLowerCase();
    for (let i = 0; i < options.length; i++) {
        const option = options[i];
        const text = option.text.toLowerCase();
        if (text.includes(lowerQuery)) {
            option.style.display = '';
        } else {
            option.style.display = 'none';
        }
    }
}

// Example: Add any interactive functionality here
console.log("My Library loaded");

// Placeholder for future JS code
