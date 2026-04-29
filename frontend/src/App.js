import React from 'react';
import { Link } from 'react-router-dom';
import './App.css';

function App() {
  return (
    <div className="app">
      {/* Navigation */}
      <nav className="navigation">
        <div className="nav-container">
          <div className="nav-logo">T</div>
          <div className="nav-links">
            <a href="#about" className="nav-link">About</a>
            <a href="#skills" className="nav-link">Skills</a>
            <a href="#contact" className="nav-link">Contact</a>
          </div>
        </div>
      </nav>

      {/* Hero Section - Dark */}
      <section className="hero-section dark-section">
        <div className="hero-content">
          <h1 className="hero-headline">张问之的技术站</h1>
          <p className="hero-subtitle">虽然现在还有点丑，但我会不断改进的！</p>
          <div className="hero-buttons">
            <Link to="/my-library" className="pill-button primary">View Projects</Link>
            <a href="#contact" className="pill-button secondary">Get In Touch</a>
          </div>
        </div>
      </section>

      {/* 其他部分保持不变... */}
      {/* About Section - Light */}
      <section className="about-section light-section">
        <div className="section-content">
          <h2 className="section-heading">关于我</h2>
          <div className="about-grid">
            <div className="about-text">
              <p className="body-text">
                我是一个快乐的程序猿，我的目标是通过代码创造出或有用或没用的应用。
              </p>
              <p className="body-text">
                代码的神奇之处，就在于它能将天马行空变成现实。。
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* Skills Section - Light */}
      <section className="skills-section light-section">
      </section>

      {/* Contact Section - Dark */}
      <section className="contact-section dark-section">
        <div className="section-content">
          <h2 className="section-heading white">Let's Work Together</h2>
          <p className="contact-subtitle">Ready to bring your ideas to life? Get in touch!</p>
          <div className="contact-info">
            <div className="contact-item">
              <span className="contact-label">Email</span>
              <a href="mailto:wenzhangzhang_dx@163.com" className="contact-value">wenzhangzhang_dx@163.com</a>
            </div>
            <div className="contact-item">
              <span className="contact-label">GitHub</span>
              <a href="https://github.com" className="contact-value">@wenzhizhang</a>
            </div>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="footer light-section">
        <div className="footer-content">
          <p><a href="https://beian.miit.gov.cn/" target="_blank">蜀ICP备2026018962号</a></p>
          <p className="footer-text">© 2024 Your Name. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}

export default App;