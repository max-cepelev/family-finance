// The edit panel: the row it shows stays highlighted; × or Escape closes it.

function closePanel() {
  const slot = document.createElement('aside');
  slot.id = 'panel';
  document.getElementById('panel').replaceWith(slot);
  highlight();
}

// Marks the row of the operation in the panel, after every swap: a new panel, a closed one, a reloaded list.
function highlight() {
  const uid = document.querySelector('#panel input[name=uid]')?.value;
  document.querySelectorAll('.row.selected').forEach(row => row.classList.remove('selected'));
  if (uid) document.querySelector(`.row[data-uid="${CSS.escape(uid)}"]`)?.classList.add('selected');
}

document.addEventListener('htmx:afterSettle', highlight);
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && document.querySelector('#panel.panel')) closePanel();
});
