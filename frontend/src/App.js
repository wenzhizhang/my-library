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
          <div className="nav-icons">
            <span>🔍</span>
            <span>🛒</span>
          </div>
        </div>
      </nav>

      {/* Hero Section - Dark */}
      <section className="hero-section dark-section">
        <div className="hero-content">
          <h1 className="hero-headline">张问之的技术站</h1>
          <p className="hero-subtitle">Building exceptional digital experiences with modern technologies</p>
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
          <h2 className="section-heading">About Me</h2>
          <div className="about-grid">
            <div className="about-text">
              <p className="body-text">
                I'm a passionate developer with expertise in React, Node.js, and modern web technologies. 
                With over 5 years of experience, I specialize in creating performant, accessible, and 
                user-friendly applications.
              </p>
              <p className="body-text">
                My approach combines technical excellence with thoughtful design, ensuring that every 
                project not only functions flawlessly but also provides an exceptional user experience.
              </p>
            </div>
            <div className="about-stats">
              <div className="stat-item">
                <span className="stat-number">50+</span>
                <span className="stat-label">Projects Completed</span>
              </div>
              <div className="stat-item">
                <span className="stat-number">5+</span>
                <span className="stat-label">Years Experience</span>
              </div>
              <div className="stat-item">
                <span className="stat-number">20+</span>
                <span className="stat-label">Technologies Mastered</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Skills Section - Light */}
      <section className="skills-section light-section">
        <div className="section-content">
          <h2 className="section-heading">Technical Skills</h2>
          <div className="skills-grid">
            <div className="skill-category">
              <h3 className="skill-category-title">Frontend</h3>
              <ul className="skill-list">
                <li className="skill-item">React & React Native</li>
                <li className="skill-item">TypeScript</li>
                <li className="skill-item">HTML5 & CSS3</li>
                <li className="skill-item">Redux & Context API</li>
                <li className="skill-item">Responsive Design</li>
              </ul>
            </div>
            <div className="skill-category">
              <h3 className="skill-category-title">Backend</h3>
              <ul className="skill-list">
                <li className="skill-item">Node.js & Express</li>
                <li className="skill-item">Python & Django</li>
                <li className="skill-item">RESTful APIs</li>
                <li className="skill-item">GraphQL</li>
                <li className="skill-item">Database Design</li>
              </ul>
            </div>
            <div className="skill-category">
              <h3 className="skill-category-title">DevOps</h3>
              <ul className="skill-list">
                <li className="skill-item">Docker & Kubernetes</li>
                <li className="skill-item">AWS & Azure</li>
                <li className="skill-item">CI/CD Pipelines</li>
                <li className="skill-item">Git & GitHub</li>
                <li className="skill-item">Testing & QA</li>
              </ul>
            </div>
          </div>
        </div>
      </section>

      {/* Contact Section - Dark */}
      <section className="contact-section dark-section">
        <div className="section-content">
          <h2 className="section-heading white">Let's Work Together</h2>
          <p className="contact-subtitle">Ready to bring your ideas to life? Get in touch!</p>
          <div className="contact-info">
            <div className="contact-item">
              <span className="contact-label">Email</span>
              <a href="mailto:hello@example.com" className="contact-value">hello@example.com</a>
            </div>
            <div className="contact-item">
              <span className="contact-label">GitHub</span>
              <a href="https://github.com" className="contact-value">@username</a>
            </div>
            <div className="contact-item">
              <span className="contact-label">LinkedIn</span>
              <a href="https://linkedin.com" className="contact-value">linkedin.com/in/username</a>
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