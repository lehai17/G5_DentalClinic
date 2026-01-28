// Utility functions
function scrollToSection(id) {
  const el = document.getElementById(id);
  if (el) el.scrollIntoView({ behavior: "smooth" });
}

function handleServiceClick(element) {
  const name = element.getAttribute("data-name");
  const price = element.getAttribute("data-price");
  alert("Dich vu: " + name + "\nGia: " + price + " VND");
}

window.loadBlogPage = function (page) {
  console.log("AJAX blog page:", page);

  const blogSection = document.getElementById("blog");
  if (!blogSection) {
    console.error("Khong tim thay the id='blog'");
    return;
  }

  blogSection.style.opacity = "0.5";

  fetch("/homepage?page=" + page)
    .then((response) => {
      if (!response.ok) throw new Error("Network response was not ok");
      return response.text();
    })
    .then((html) => {
      const parser = new DOMParser();
      const doc = parser.parseFromString(html, "text/html");
      const blogEl = doc.querySelector("#blog");
      if (!blogEl) throw new Error("Response khong co #blog");

      blogSection.innerHTML = blogEl.innerHTML;
      blogSection.style.opacity = "1";
      blogSection.scrollIntoView({ behavior: "smooth", block: "start" });
      console.log("Update blog OK:", page);
    })
    .catch((err) => {
      console.error("Loi AJAX:", err);
      blogSection.style.opacity = "1";
    });
};

// Close modal on outside click
window.addEventListener("click", function (event) {
  if (event.target.classList && event.target.classList.contains("modal")) {
    event.target.classList.remove("active");
  }
});

console.log("home.js loaded");

// IMPORTANT: must be global for onclick="toggleSidebar()"
window.toggleSidebar = function () {
  const sidebar = document.getElementById("sidebar");
  const mainContent = document.querySelector(".main-content");

  if (!sidebar) return console.error("Khong tim thay #sidebar");
  if (!mainContent) return console.error("Khong tim thay .main-content");

  sidebar.classList.toggle("collapsed");
  mainContent.classList.toggle("expanded");

  console.log("toggleSidebar OK");
};
